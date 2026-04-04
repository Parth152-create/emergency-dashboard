
package com.parth.emergency_dashboard.controller;

import com.parth.emergency_dashboard.model.Emergency;
import com.parth.emergency_dashboard.service.EmergencyService;
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

    public EmergencyController(EmergencyService service,
                               SimpMessagingTemplate messagingTemplate) {
        this.service = service;
        this.messagingTemplate = messagingTemplate;
    }

    // ✅ POST — add new emergency, push to admin board
    @PostMapping
    public Emergency addEmergency(@RequestBody @Valid Emergency emergency) {
        Emergency saved = service.addEmergency(emergency);

        // Push to admin dashboard in real-time
        messagingTemplate.convertAndSend("/topic/emergencies", saved);

        return saved;
    }

    // ✅ GET ALL
    @GetMapping
    public List<Emergency> getAll() {
        return service.getAllEmergencies();
    }

    // ✅ NEW: GET by priority
    @GetMapping("/priority/{priority}")
    public List<Emergency> getByPriority(@PathVariable String priority) {
        return service.getByPriority(priority);
    }

    // ✅ NEW: GET by status
    @GetMapping("/status/{status}")
    public List<Emergency> getByStatus(@PathVariable String status) {
        return service.getByStatus(status);
    }

    // ✅ NEW: Search by location
    @GetMapping("/search")
    public List<Emergency> search(@RequestParam String location) {
        return service.searchByLocation(location);
    }

    // ✅ GET BY ID
    @GetMapping("/{id}")
    public Emergency getById(@PathVariable Long id) {
        return service.getById(id);
    }

    // ✅ GET BY TRACKING ID (customer tracking)
    @GetMapping("/track/{trackingId}")
    public ResponseEntity<Emergency> getByTrackingId(@PathVariable String trackingId) {
        Emergency emergency = service.getByTrackingId(trackingId);
        if (emergency == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(emergency);
    }

    // ✅ UPDATE — push status change to both admin + customer via WebSocket
    @PutMapping("/{id}")
    public Emergency update(@PathVariable Long id, @RequestBody Emergency emergency) {
        Emergency updated = service.updateEmergency(id, emergency);

        if (updated != null) {
            // Push to admin dashboard
            messagingTemplate.convertAndSend("/topic/emergencies", updated);

            // Push to customer tracking their specific emergency
            messagingTemplate.convertAndSend(
                "/topic/track/" + updated.getTrackingId(), updated
            );
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
