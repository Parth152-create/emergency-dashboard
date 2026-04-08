package com.parth.emergency_dashboard.service;

import com.parth.emergency_dashboard.model.Emergency;
import com.parth.emergency_dashboard.repository.EmergencyRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EmergencyService {

    private final EmergencyRepository emergencyRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AiClassificationService aiClassificationService;

    public EmergencyService(EmergencyRepository emergencyRepository,
                            SimpMessagingTemplate messagingTemplate,
                            AiClassificationService aiClassificationService) {
        this.emergencyRepository = emergencyRepository;
        this.messagingTemplate = messagingTemplate;
        this.aiClassificationService = aiClassificationService;
    }

    /**
     * Creates a new emergency, auto-classifies priority via AI, then broadcasts via WebSocket.
     * Called when a citizen submits a report.
     */
    public Emergency createEmergency(Emergency emergency) {
        // Generate tracking ID if not present
        if (emergency.getTrackingId() == null || emergency.getTrackingId().isBlank()) {
            emergency.setTrackingId(generateTrackingId());
        }

        // Set default status if not provided
        if (emergency.getStatus() == null || emergency.getStatus().isBlank()) {
            emergency.setStatus("OPEN");
        }

        // ── AI PRIORITY CLASSIFICATION ───────────────────────────────────────
        // Always classify via AI — overrides whatever priority the citizen sent
        String aiPriority = aiClassificationService.classifyPriority(
                emergency.getType(),
                emergency.getLocation(),
                emergency.getDescription()
        );
        emergency.setPriority(aiPriority);
        emergency.setAiClassified(true);
        // ─────────────────────────────────────────────────────────────────────

        Emergency saved = emergencyRepository.save(emergency);

        // Broadcast real-time update to all WebSocket subscribers
        messagingTemplate.convertAndSend("/topic/emergencies", saved);

        return saved;
    }

    /**
     * Admin manually re-triggers AI classification for an existing emergency.
     * Useful if the report was edited after submission.
     */
    public Emergency reclassifyEmergency(Long id) {
        Emergency emergency = emergencyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Emergency not found: " + id));

        String aiPriority = aiClassificationService.classifyPriority(
                emergency.getType(),
                emergency.getLocation(),
                emergency.getDescription()
        );
        emergency.setPriority(aiPriority);
        emergency.setAiClassified(true);

        Emergency saved = emergencyRepository.save(emergency);

        // Broadcast the updated emergency
        messagingTemplate.convertAndSend("/topic/emergencies", saved);

        return saved;
    }

    /**
     * Admin manually overrides the AI-assigned priority.
     * Sets aiClassified = false so the dashboard can show a "manual override" badge.
     */
    public Emergency overridePriority(Long id, String newPriority) {
        String normalised = newPriority.toUpperCase().trim();
        if (!List.of("HIGH", "MEDIUM", "LOW").contains(normalised)) {
            throw new IllegalArgumentException("Priority must be HIGH, MEDIUM, or LOW");
        }

        Emergency emergency = emergencyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Emergency not found: " + id));

        emergency.setPriority(normalised);
        emergency.setAiClassified(false); // mark as manually set

        Emergency saved = emergencyRepository.save(emergency);
        messagingTemplate.convertAndSend("/topic/emergencies", saved);

        return saved;
    }

    public List<Emergency> getAllEmergencies() {
        return emergencyRepository.findAll();
    }

    public Emergency getEmergencyById(Long id) {
        return emergencyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Emergency not found: " + id));
    }

    public Emergency getEmergencyByTrackingId(String trackingId) {
        return emergencyRepository.findByTrackingId(trackingId)
                .orElseThrow(() -> new RuntimeException("Tracking ID not found: " + trackingId));
    }

    public Emergency updateEmergency(Long id, Emergency updated) {
        Emergency existing = getEmergencyById(id);
        existing.setType(updated.getType());
        existing.setLocation(updated.getLocation());
        existing.setStatus(updated.getStatus());
        existing.setLatitude(updated.getLatitude());
        existing.setLongitude(updated.getLongitude());
        existing.setDescription(updated.getDescription());
        // Note: priority is NOT updated here — use overridePriority() or reclassifyEmergency()
        return emergencyRepository.save(existing);
    }

    public void deleteEmergency(Long id) {
        emergencyRepository.deleteById(id);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateTrackingId() {
        return "TRK-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}