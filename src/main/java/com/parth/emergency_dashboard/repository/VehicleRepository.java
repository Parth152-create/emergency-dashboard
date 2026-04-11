package com.parth.emergency_dashboard.repository;

import com.parth.emergency_dashboard.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByVehicleStatus(String status);
    Optional<Vehicle> findByAssignedEmergencyId(Long emergencyId);
    List<Vehicle> findByTypeAndVehicleStatus(String type, String status);
}