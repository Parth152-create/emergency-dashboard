package com.parth.emergency_dashboard.service;

import com.parth.emergency_dashboard.model.Emergency;
import com.parth.emergency_dashboard.repository.EmergencyRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EmergencyService {

    private final EmergencyRepository emergencyRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AiClassificationService aiClassificationService;
    private final VehicleSimulationService vehicleSimulationService;

    public EmergencyService(EmergencyRepository emergencyRepository,
                            SimpMessagingTemplate messagingTemplate,
                            AiClassificationService aiClassificationService,
                            // @Lazy breaks the circular dependency (VehicleSimulationService
                            // autowires EmergencyRepository directly, not EmergencyService)
                            @Lazy VehicleSimulationService vehicleSimulationService) {
        this.emergencyRepository      = emergencyRepository;
        this.messagingTemplate        = messagingTemplate;
        this.aiClassificationService  = aiClassificationService;
        this.vehicleSimulationService = vehicleSimulationService;
    }

    public Emergency createEmergency(Emergency emergency) {
        // Generate tracking ID
        if (emergency.getTrackingId() == null || emergency.getTrackingId().isBlank()) {
            emergency.setTrackingId(generateTrackingId());
        }

        // Default status
        if (emergency.getStatus() == null || emergency.getStatus().isBlank()) {
            emergency.setStatus("REPORTED");
        }

        // ── AI priority classification ────────────────────────────────────────
        String aiPriority = aiClassificationService.classifyPriority(
                emergency.getType(),
                emergency.getLocation(),
                emergency.getDescription()
        );
        emergency.setPriority(aiPriority);
        emergency.setAiClassified(true);

        Emergency saved = emergencyRepository.save(emergency);

        // Broadcast new emergency to admin dashboard
        messagingTemplate.convertAndSend("/topic/emergencies", saved);

        // ── Dispatch a vehicle ────────────────────────────────────────────────
        // Only dispatch if coordinates are present (citizen pinned location)
        if (saved.getLatitude() != null && saved.getLongitude() != null) {
            vehicleSimulationService.dispatchVehicle(saved);
        }

        return saved;
    }

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
        messagingTemplate.convertAndSend("/topic/emergencies", saved);
        return saved;
    }

    public Emergency overridePriority(Long id, String newPriority) {
        String normalised = newPriority.toUpperCase().trim();
        if (!List.of("HIGH", "MEDIUM", "LOW").contains(normalised)) {
            throw new IllegalArgumentException("Priority must be HIGH, MEDIUM, or LOW");
        }
        Emergency emergency = emergencyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Emergency not found: " + id));
        emergency.setPriority(normalised);
        emergency.setAiClassified(false);
        Emergency saved = emergencyRepository.save(emergency);
        messagingTemplate.convertAndSend("/topic/emergencies", saved);
        return saved;
    }

    public List<Emergency> getAllEmergencies() { return emergencyRepository.findAll(); }

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
        Emergency saved = emergencyRepository.save(existing);
        messagingTemplate.convertAndSend("/topic/emergencies", saved);
        return saved;
    }

    public void deleteEmergency(Long id) { emergencyRepository.deleteById(id); }

    private String generateTrackingId() {
        return "TRK-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}