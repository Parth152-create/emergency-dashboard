package com.parth.emergency_dashboard.repository;

import com.parth.emergency_dashboard.model.Emergency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmergencyRepository extends JpaRepository<Emergency, Long> {

    // ✅ Find by tracking ID (for customer tracking page)
    Optional<Emergency> findByTrackingId(String trackingId);
    List<Emergency> findByPriority(String priority);
List<Emergency> findByStatus(String status);
List<Emergency> findByLocationContainingIgnoreCase(String location);
}