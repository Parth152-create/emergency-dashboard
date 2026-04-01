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

    // ✅ Add emergency — auto-generate tracking ID
    public Emergency addEmergency(Emergency emergency) {
        // Generate unique tracking ID like "TRK-A3F9X2"
        String trackingId = "TRK-" + UUID.randomUUID()
                .toString()
                .toUpperCase()
                .replace("-", "")
                .substring(0, 6);
        emergency.setTrackingId(trackingId);
        return repository.save(emergency);
    }

    // ✅ Get all emergencies
    public List<Emergency> getAllEmergencies() {
        return repository.findAll();
    }

    // ✅ Get by ID
    public Emergency getById(Long id) {
        return repository.findById(id).orElse(null);
    }

    // ✅ Get by tracking ID (for customer tracking)
    public Emergency getByTrackingId(String trackingId) {
        return repository.findByTrackingId(trackingId).orElse(null);
    }

    // ✅ Update emergency
    public Emergency updateEmergency(Long id, Emergency updated) {
        Emergency existing = repository.findById(id).orElse(null);
        if (existing != null) {
            existing.setType(updated.getType());
            existing.setLocation(updated.getLocation());
            existing.setStatus(updated.getStatus());
            existing.setLatitude(updated.getLatitude());
            existing.setLongitude(updated.getLongitude());
            return repository.save(existing);
        }
        return null;
    }

    // ✅ Delete emergency
    public void deleteEmergency(Long id) {
        repository.deleteById(id);
    }
}