package com.parth.emergency_dashboard.service;

import com.parth.emergency_dashboard.model.Emergency;
import com.parth.emergency_dashboard.repository.EmergencyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EmergencyService {

    private final EmergencyRepository repository;

    public EmergencyService(EmergencyRepository repository) {
        this.repository = repository;
    }

    // ✅ Add emergency — auto-generate tracking ID + auto-set priority
    public Emergency addEmergency(Emergency emergency) {

        String trackingId = "TRK-" + UUID.randomUUID()
                .toString()
                .toUpperCase()
                .replace("-", "")
                .substring(0, 6);
        emergency.setTrackingId(trackingId);

        // ✅ Auto-assign priority ONLY if not provided
        if (emergency.getPriority() == null || emergency.getPriority().isEmpty()) {
            emergency.setPriority(determinePriority(emergency.getType()));
        }

        return repository.save(emergency);
    }

    // ✅ Get all emergencies (sorted by priority: HIGH → MEDIUM → LOW)
    public List<Emergency> getAllEmergencies() {
        List<Emergency> list = repository.findAll();
        list.sort((e1, e2) ->
                getPriorityValue(e2.getPriority()) - getPriorityValue(e1.getPriority())
        );
        return list;
    }

    // ✅ Get emergencies by priority
    public List<Emergency> getByPriority(String priority) {
        return repository.findByPriority(priority);
    }

    // ✅ Get emergencies by status
    public List<Emergency> getByStatus(String status) {
        return repository.findByStatus(status);
    }

    // ✅ Search by location
    public List<Emergency> searchByLocation(String location) {
        return repository.findByLocationContainingIgnoreCase(location);
    }

    // ✅ Get by ID
    public Emergency getById(Long id) {
        return repository.findById(id).orElse(null);
    }

    // ✅ Get by tracking ID
    public Emergency getByTrackingId(String trackingId) {
        return repository.findByTrackingId(trackingId).orElse(null);
    }

    // ✅ Update emergency — ALL fields preserved so nothing gets wiped on resolve
    public Emergency updateEmergency(Long id, Emergency updated) {
        Emergency existing = repository.findById(id).orElse(null);

        if (existing != null) {
            existing.setType(updated.getType());
            existing.setLocation(updated.getLocation());
            existing.setStatus(updated.getStatus());
            existing.setLatitude(updated.getLatitude());
            existing.setLongitude(updated.getLongitude());

            // ✅ Preserve reporter info — don't wipe on resolve
            if (updated.getReporterName() != null) existing.setReporterName(updated.getReporterName());
            if (updated.getReporterPhone() != null) existing.setReporterPhone(updated.getReporterPhone());

            // ✅ Handle priority — keep existing if not provided
            if (updated.getPriority() != null && !updated.getPriority().isEmpty()) {
                existing.setPriority(updated.getPriority());
            } else {
                existing.setPriority(determinePriority(updated.getType()));
            }

            return repository.save(existing);
        }
        return null;
    }

    // ✅ Delete emergency
    public void deleteEmergency(Long id) {
        repository.deleteById(id);
    }

    // ✅ Helper: auto-determine priority from type
    private String determinePriority(String type) {
        if (type == null) return "LOW";
        switch (type.toLowerCase()) {
            case "fire":
            case "accident":
            case "medical":
                return "HIGH";
            case "theft":
            case "robbery":
                return "MEDIUM";
            default:
                return "LOW";
        }
    }

    // ✅ Helper: numeric value for sorting
    private int getPriorityValue(String priority) {
        if (priority == null) return 0;
        switch (priority) {
            case "HIGH":   return 3;
            case "MEDIUM": return 2;
            case "LOW":    return 1;
            default:       return 0;
        }
    }
}