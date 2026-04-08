package com.parth.emergency_dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class AiClassificationService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama3-8b-8192"; // fast + free on Groq

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Classifies an emergency and returns HIGH, MEDIUM, or LOW.
     * Falls back to "MEDIUM" if AI call fails, so the app never breaks.
     *
     * @param type        Emergency type (e.g. "Fire", "Flood", "Medical")
     * @param location    Location string (e.g. "MG Road, Bhopal")
     * @param description Free-text description from the reporter (can be null)
     * @return "HIGH", "MEDIUM", or "LOW"
     */
    public String classifyPriority(String type, String location, String description) {
        try {
            String prompt = buildPrompt(type, location, description);
            String requestBody = buildRequestBody(prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parsePriority(response.body());
            } else {
                System.err.println("[AiClassification] Groq API error: " + response.statusCode()
                        + " — " + response.body());
                return "MEDIUM"; // safe fallback
            }

        } catch (Exception e) {
            System.err.println("[AiClassification] Exception: " + e.getMessage());
            return "MEDIUM"; // safe fallback — never crash the app
        }
    }

    private String buildPrompt(String type, String location, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an emergency dispatch AI. Classify the following emergency report as HIGH, MEDIUM, or LOW priority.\n\n");
        sb.append("Rules:\n");
        sb.append("- HIGH: Immediate threat to life, active fire/violence/serious injury, large-scale disaster\n");
        sb.append("- MEDIUM: Urgent but not immediately life-threatening, property damage, medical non-critical\n");
        sb.append("- LOW: Minor incidents, precautionary reports, non-urgent situations\n\n");
        sb.append("Emergency Type: ").append(type != null ? type : "Unknown").append("\n");
        sb.append("Location: ").append(location != null ? location : "Unknown").append("\n");
        if (description != null && !description.isBlank()) {
            sb.append("Description: ").append(description).append("\n");
        }
        sb.append("\nRespond with EXACTLY one word — HIGH, MEDIUM, or LOW. No explanation, no punctuation.");
        return sb.toString();
    }

    private String buildRequestBody(String prompt) throws Exception {
        // Build JSON manually to avoid extra dependencies
        return objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                        .put("model", MODEL)
                        .put("max_tokens", 10)
                        .put("temperature", 0.0) // deterministic output
                        .set("messages", objectMapper.createArrayNode()
                                .add(objectMapper.createObjectNode()
                                        .put("role", "user")
                                        .put("content", prompt)))
        );
    }

    private String parsePriority(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText("")
                .trim()
                .toUpperCase();

        // Defensive parsing — extract the keyword even if model adds extra words
        if (content.contains("HIGH"))   return "HIGH";
        if (content.contains("LOW"))    return "LOW";
        if (content.contains("MEDIUM")) return "MEDIUM";

        System.err.println("[AiClassification] Unexpected response: " + content);
        return "MEDIUM"; // fallback
    }
}