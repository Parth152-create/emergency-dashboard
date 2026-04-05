package com.parth.emergency_dashboard.controller;

import com.parth.emergency_dashboard.model.Emergency;
import com.parth.emergency_dashboard.service.EmergencyService;
import com.parth.emergency_dashboard.service.SmsService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

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

    // ✅ POST — customer reports emergency
    @PostMapping
    public Emergency addEmergency(@RequestBody @Valid Emergency emergency) {
        Emergency saved = service.addEmergency(emergency);

        // 📡 Real-time push to admin dashboard
        messagingTemplate.convertAndSend("/topic/emergencies", saved);

        // 📱 SMS confirmation to reporter
        if (saved.getReporterPhone() != null && !saved.getReporterPhone().isEmpty()) {
            smsService.sendReportConfirmation(
                saved.getReporterPhone(),
                saved.getType(),
                saved.getLocation(),
                saved.getTrackingId(),
                saved.getPriority()
            );
        }

        return saved;
    }

    // ✅ GET ALL
    @GetMapping
    public List<Emergency> getAll() {
        return service.getAllEmergencies();
    }

    // ✅ GET BY ID
    @GetMapping("/{id}")
    public Emergency getById(@PathVariable Long id) {
        return service.getById(id);
    }

    // ✅ GET BY TRACKING ID (customer tracking — public)
    @GetMapping("/track/{trackingId}")
    public ResponseEntity<Emergency> getByTrackingId(@PathVariable String trackingId) {
        Emergency emergency = service.getByTrackingId(trackingId);
        if (emergency == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(emergency);
    }

    // ✅ GET BY PRIORITY
    @GetMapping("/priority/{priority}")
    public List<Emergency> getByPriority(@PathVariable String priority) {
        return service.getByPriority(priority);
    }

    // ✅ GET BY STATUS
    @GetMapping("/status/{status}")
    public List<Emergency> getByStatus(@PathVariable String status) {
        return service.getByStatus(status);
    }

    // ✅ UPDATE — admin resolves or edits
    @PutMapping("/{id}")
    public Emergency update(@PathVariable Long id, @RequestBody Emergency emergency) {
        boolean wasActive = false;
        Emergency existing = service.getById(id);
        if (existing != null) {
            wasActive = !"RESOLVED".equals(existing.getStatus());
        }

        Emergency updated = service.updateEmergency(id, emergency);

        if (updated != null) {
            // 📡 Push to admin dashboard
            messagingTemplate.convertAndSend("/topic/emergencies", updated);

            // 📡 Push to customer tracking their specific emergency
            messagingTemplate.convertAndSend("/topic/track/" + updated.getTrackingId(), updated);

            // 📱 SMS to reporter when newly resolved
            boolean nowResolved = "RESOLVED".equals(updated.getStatus());
            if (wasActive && nowResolved && updated.getReporterPhone() != null
                    && !updated.getReporterPhone().isEmpty()) {
                smsService.sendResolveNotification(
                    updated.getReporterPhone(),
                    updated.getType(),
                    updated.getLocation(),
                    updated.getTrackingId()
                );
            }
        }

        return updated;
    }

    // ✅ DELETE
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        service.deleteEmergency(id);
        return "Deleted successfully";
    }
}