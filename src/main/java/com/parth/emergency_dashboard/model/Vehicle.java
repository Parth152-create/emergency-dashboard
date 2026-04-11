package com.parth.emergency_dashboard.model;

import jakarta.persistence.*;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;         // e.g. "Ambulance 1"
    private String type;         // AMBULANCE, FIRE_TRUCK, POLICE_CAR

    // Current position (moves each tick)
    private Double currentLat;
    private Double currentLng;

    // Start/home position (nearest resource to the emergency)
    private Double homeLat;
    private Double homeLng;

    // Target emergency
    private Long assignedEmergencyId;
    private Double targetLat;
    private Double targetLng;

    // IDLE | DISPATCHED | IN_PROGRESS | RESOLVED
    private String vehicleStatus;

    public Vehicle() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Double getCurrentLat() { return currentLat; }
    public void setCurrentLat(Double currentLat) { this.currentLat = currentLat; }

    public Double getCurrentLng() { return currentLng; }
    public void setCurrentLng(Double currentLng) { this.currentLng = currentLng; }

    public Double getHomeLat() { return homeLat; }
    public void setHomeLat(Double homeLat) { this.homeLat = homeLat; }

    public Double getHomeLng() { return homeLng; }
    public void setHomeLng(Double homeLng) { this.homeLng = homeLng; }

    public Long getAssignedEmergencyId() { return assignedEmergencyId; }
    public void setAssignedEmergencyId(Long assignedEmergencyId) { this.assignedEmergencyId = assignedEmergencyId; }

    public Double getTargetLat() { return targetLat; }
    public void setTargetLat(Double targetLat) { this.targetLat = targetLat; }

    public Double getTargetLng() { return targetLng; }
    public void setTargetLng(Double targetLng) { this.targetLng = targetLng; }

    public String getVehicleStatus() { return vehicleStatus; }
    public void setVehicleStatus(String vehicleStatus) { this.vehicleStatus = vehicleStatus; }
}