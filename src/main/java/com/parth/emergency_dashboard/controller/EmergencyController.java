package com.parth.emergency_dashboard.controller;

import com.parth.emergency_dashboard.model.Emergency;
import com.parth.emergency_dashboard.service.EmergencyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emergencies")
public class EmergencyController {

    private final EmergencyService emergencyService;

    public EmergencyController(EmergencyService emergencyService) {
        this.emergencyService = emergencyService;
    }

    // ── PUBLIC endpoints (citizen-facing) ────────────────────────────────────

    /** Submit a new emergency report. AI auto-classifies priority. */
    @PostMapping
    public ResponseEntity<Emergency> createEmergency(@Valid @RequestBody Emergency emergency) {
        return ResponseEntity.ok(emergencyService.createEmergency(emergency));
    }

    /** Track an emergency by tracking ID (citizen polling) */
    @GetMapping("/track/{trackingId}")
    public ResponseEntity<Emergency> trackEmergency(@PathVariable String trackingId) {
        return ResponseEntity.ok(emergencyService.getEmergencyByTrackingId(trackingId));
    }

    // ── ADMIN endpoints (JWT protected via SecurityConfig) ───────────────────

    @GetMapping
    public ResponseEntity<List<Emergency>> getAllEmergencies() {
        return ResponseEntity.ok(emergencyService.getAllEmergencies());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Emergency> getEmergencyById(@PathVariable Long id) {
        return ResponseEntity.ok(emergencyService.getEmergencyById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Emergency> updateEmergency(@PathVariable Long id,
                                                      @RequestBody Emergency emergency) {
        return ResponseEntity.ok(emergencyService.updateEmergency(id, emergency));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmergency(@PathVariable Long id) {
        emergencyService.deleteEmergency(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Admin: Re-run AI classification on an existing emergency.
     * POST /api/emergencies/{id}/reclassify
     */
    @PostMapping("/{id}/reclassify")
    public ResponseEntity<Emergency> reclassify(@PathVariable Long id) {
        return ResponseEntity.ok(emergencyService.reclassifyEmergency(id));
    }

    /**
     * Admin: Manually override priority.
     * PATCH /api/emergencies/{id}/priority
     * Body: { "priority": "HIGH" }
     */
    @PatchMapping("/{id}/priority")
    public ResponseEntity<Emergency> overridePriority(@PathVariable Long id,
                                                       @RequestBody Map<String, String> body) {
        String priority = body.get("priority");
        if (priority == null || priority.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(emergencyService.overridePriority(id, priority));
    }
}