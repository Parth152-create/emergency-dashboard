package com.parth.emergency_dashboard.controller;

import com.parth.emergency_dashboard.model.Vehicle;
import com.parth.emergency_dashboard.service.VehicleSimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleSimulationService vehicleSimulationService;

    public VehicleController(VehicleSimulationService vehicleSimulationService) {
        this.vehicleSimulationService = vehicleSimulationService;
    }

    /** Admin: get current state of all vehicles */
    @GetMapping
    public ResponseEntity<List<Vehicle>> getAllVehicles() {
        return ResponseEntity.ok(vehicleSimulationService.getAllVehicles());
    }
}