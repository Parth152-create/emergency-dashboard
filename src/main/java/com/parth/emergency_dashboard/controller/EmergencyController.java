package com.parth.emergency_dashboard.controller;

import com.parth.emergency_dashboard.model.Emergency;
import com.parth.emergency_dashboard.service.EmergencyService;
import com.parth.emergency_dashboard.service.SmsService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emergencies")
@CrossOrigin(origins = "*")
public class EmergencyController {

    private final EmergencyService service;
    private final SimpMessagingTemplate messagingTemplate;
    private final SmsService smsService;

    public EmergencyController(EmergencyService service,
                               SimpMessagingTemplate messagingTemplate,
                               SmsService smsService) {
        this.service = service;
        this.messagingTemplate = messagingTemplate;
        this.smsService = smsService;
    }

    // ── PUBLIC: Reporter submits emergency ────────────
    @PostMapping
    public Emergency addEmergency(@RequestBody @Valid Emergency emergency) {
        Emergency saved = service.addEmergency(emergency);
        messagingTemplate.convertAndSend("/topic/emergencies", saved);
        if (saved.getReporterPhone() != null && !saved.getReporterPhone().isEmpty()) {
            smsService.sendReportConfirmation(
                saved.getReporterPhone(), saved.getType(),
                saved.getLocation(), saved.getTrackingId(), saved.getPriority()
            );
        }
        return saved;
    }

    // ── ADMIN: Get all emergencies ────────────────────
    @GetMapping
    public List<Emergency> getAll() {
        return service.getAllEmergencies();
    }

    // ── ADMIN: Get by ID ──────────────────────────────
    @GetMapping("/{id}")
    public Emergency getById(@PathVariable Long id) {
        return service.getById(id);
    }

    // ── PUBLIC: Reporter tracks by tracking ID ────────
    @GetMapping("/track/{trackingId}")
    public ResponseEntity<Emergency> getByTrackingId(@PathVariable String trackingId) {
        Emergency emergency = service.getByTrackingId(trackingId);
        if (emergency == null) return ResponseEntity.notFound().build();
        // Return limited info — no full list, just their own record
        return ResponseEntity.ok(emergency);
    }

    // ── PUBLIC: Reporter verifies identity (name + phone) ──
    // Used on Track tab — verify the reporter owns the tracking ID
    @PostMapping("/verify")
    public ResponseEntity<?> verifyReporter(@RequestBody Map<String, String> body) {
        String trackingId   = body.get("trackingId");
        String reporterName  = body.get("reporterName");
        String reporterPhone = body.get("reporterPhone");

        if (trackingId == null || reporterName == null || reporterPhone == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "trackingId, reporterName and reporterPhone are required"));
        }

        Emergency emergency = service.getByTrackingId(trackingId.toUpperCase().trim());

        if (emergency == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "No emergency found with that tracking ID"));
        }

        // Check name AND phone match (case-insensitive name check)
        boolean nameMatch  = reporterName.trim().equalsIgnoreCase(emergency.getReporterName());
        boolean phoneMatch = reporterPhone.trim().equals(emergency.getReporterPhone());

        if (!nameMatch || !phoneMatch) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Name or phone number does not match our records"));
        }

        // ✅ Verified — return full emergency details
        return ResponseEntity.ok(emergency);
    }

    // ── ADMIN: Get by priority ────────────────────────
    @GetMapping("/priority/{priority}")
    public List<Emergency> getByPriority(@PathVariable String priority) {
        return service.getByPriority(priority);
    }

    // ── ADMIN: Get by status ──────────────────────────
    @GetMapping("/status/{status}")
    public List<Emergency> getByStatus(@PathVariable String status) {
        return service.getByStatus(status);
    }

    // ── ADMIN: Update (resolve etc.) ──────────────────
    @PutMapping("/{id}")
    public Emergency update(@PathVariable Long id, @RequestBody Emergency emergency) {
        boolean wasActive = false;
        Emergency existing = service.getById(id);
        if (existing != null) wasActive = !"RESOLVED".equals(existing.getStatus());

        Emergency updated = service.updateEmergency(id, emergency);

        if (updated != null) {
            messagingTemplate.convertAndSend("/topic/emergencies", updated);
            messagingTemplate.convertAndSend("/topic/track/" + updated.getTrackingId(), updated);

            boolean nowResolved = "RESOLVED".equals(updated.getStatus());
            if (wasActive && nowResolved && updated.getReporterPhone() != null
                    && !updated.getReporterPhone().isEmpty()) {
                smsService.sendResolveNotification(
                    updated.getReporterPhone(), updated.getType(),
                    updated.getLocation(), updated.getTrackingId()
                );
            }
        }
        return updated;
    }

    // ── ADMIN: Delete ─────────────────────────────────
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        service.deleteEmergency(id);
        return "Deleted successfully";
    }
}