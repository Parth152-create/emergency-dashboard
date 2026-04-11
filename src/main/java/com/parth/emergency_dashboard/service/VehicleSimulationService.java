package com.parth.emergency_dashboard.service;

import com.parth.emergency_dashboard.model.Emergency;
import com.parth.emergency_dashboard.model.Vehicle;
import com.parth.emergency_dashboard.repository.EmergencyRepository;
import com.parth.emergency_dashboard.repository.VehicleRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VehicleSimulationService {

    // ── Bhopal resources — mirrors the frontend RESOURCES array ──────────────
    // Each entry: { name, type, lat, lng }
    private static final double[][] RESOURCES = {
        {23.2599, 77.4664}, // AIIMS Bhopal          — hospital
        {23.2584, 77.4011}, // Hamidia Hospital       — hospital
        {23.2450, 77.4264}, // Bansal Hospital        — hospital
        {23.1956, 77.4390}, // Apollo Sage            — hospital
        {23.2990, 77.4980}, // Chirayu Medical        — hospital
        {23.2338, 77.4340}, // MP Nagar Fire          — fire
        {23.2450, 77.3980}, // TT Nagar Fire          — fire
        {23.2680, 77.4780}, // Govindpura Fire        — fire
        {23.1980, 77.4640}, // Misrod Fire            — fire
        {23.2320, 77.4310}, // MP Nagar Police        — police
        {23.2430, 77.3960}, // TT Nagar Police        — police
        {23.2290, 77.4590}, // Habibganj Police       — police
        {23.2075, 77.4570}, // Shahpura Police        — police
        {23.2780, 77.4890}, // Ayodhya Bypass Police  — police
    };
    private static final String[] RESOURCE_TYPES = {
        "hospital","hospital","hospital","hospital","hospital",
        "fire","fire","fire","fire",
        "police","police","police","police","police"
    };

    // Vehicle type matched to emergency type
    private static final String vehicleTypeFor(String emergencyType) {
        if (emergencyType == null) return "AMBULANCE";
        return switch (emergencyType.toUpperCase()) {
            case "FIRE"     -> "FIRE_TRUCK";
            case "POLICE"   -> "POLICE_CAR";
            default         -> "AMBULANCE"; // MEDICAL, ACCIDENT, FLOOD, OTHER
        };
    }

    // Resource type matched to vehicle type
    private static final String resourceTypeFor(String vehicleType) {
        return switch (vehicleType) {
            case "FIRE_TRUCK"  -> "fire";
            case "POLICE_CAR"  -> "police";
            default            -> "hospital";
        };
    }

    /**
     * Realistic speed: ~60 km/h in degrees/tick (2-second tick).
     * 60 km/h = 0.01667 km/s = 0.03333 km per 2s tick
     * 1 degree latitude ≈ 111 km → 0.03333/111 ≈ 0.0003 degrees/tick
     * This gives ~2 minute travel time across typical Bhopal distances (2-4 km)
     */
    private static final double STEP = 0.0003;

    /** Arrival threshold in degrees (~33 metres) */
    private static final double ARRIVAL_THRESHOLD = 0.0003;

    private final VehicleRepository vehicleRepo;
    private final EmergencyRepository emergencyRepo;
    private final SimpMessagingTemplate ws;

    public VehicleSimulationService(VehicleRepository vehicleRepo,
                                     EmergencyRepository emergencyRepo,
                                     SimpMessagingTemplate ws) {
        this.vehicleRepo  = vehicleRepo;
        this.emergencyRepo = emergencyRepo;
        this.ws           = ws;
    }

    // ── Seed 3 idle vehicles on startup ──────────────────────────────────────
    @PostConstruct
    public void seedVehicles() {
        if (vehicleRepo.count() > 0) return; // already seeded

        createVehicle("Ambulance 1",   "AMBULANCE",   23.2599, 77.4664); // AIIMS
        createVehicle("Fire Truck 1",  "FIRE_TRUCK",  23.2338, 77.4340); // MP Nagar Fire
        createVehicle("Police Car 1",  "POLICE_CAR",  23.2320, 77.4310); // MP Nagar Police
    }

    private void createVehicle(String name, String type, double lat, double lng) {
        Vehicle v = new Vehicle();
        v.setName(name);
        v.setType(type);
        v.setCurrentLat(lat);
        v.setCurrentLng(lng);
        v.setHomeLat(lat);
        v.setHomeLng(lng);
        v.setVehicleStatus("IDLE");
        vehicleRepo.save(v);
    }

    // ── Called by EmergencyService when a new emergency is created ────────────
    public void dispatchVehicle(Emergency emergency) {
        if (emergency.getLatitude() == null || emergency.getLongitude() == null) return;

        String vehicleType = vehicleTypeFor(emergency.getType());

        // 1. Find an IDLE vehicle of the right type
        List<Vehicle> idle = vehicleRepo.findByTypeAndVehicleStatus(vehicleType, "IDLE");

        // 2. Fall back to any IDLE vehicle if none of the right type
        if (idle.isEmpty()) {
            idle = vehicleRepo.findByVehicleStatus("IDLE");
        }
        if (idle.isEmpty()) return; // all busy

        Vehicle vehicle = idle.get(0);

        // 3. Find nearest matching resource as the start position
        String resType = resourceTypeFor(vehicleType);
        double[] nearest = nearestResource(emergency.getLatitude(), emergency.getLongitude(), resType);

        // 4. Assign and set to DISPATCHED
        vehicle.setCurrentLat(nearest[0]);
        vehicle.setCurrentLng(nearest[1]);
        vehicle.setHomeLat(nearest[0]);
        vehicle.setHomeLng(nearest[1]);
        vehicle.setTargetLat(emergency.getLatitude());
        vehicle.setTargetLng(emergency.getLongitude());
        vehicle.setAssignedEmergencyId(emergency.getId());
        vehicle.setVehicleStatus("DISPATCHED");
        vehicleRepo.save(vehicle);

        // 5. Update emergency status to DISPATCHED
        emergency.setStatus("DISPATCHED");
        emergencyRepo.save(emergency);

        broadcastAll();
    }

    // ── Tick: runs every 2 seconds ────────────────────────────────────────────
    @Scheduled(fixedDelay = 2000)
    public void tick() {
        List<Vehicle> active = vehicleRepo.findAll().stream()
                .filter(v -> !"IDLE".equals(v.getVehicleStatus()))
                .toList();

        if (active.isEmpty()) return;

        boolean anyMoved = false;

        for (Vehicle v : active) {
            if (v.getTargetLat() == null || v.getTargetLng() == null) continue;

            double dLat = v.getTargetLat() - v.getCurrentLat();
            double dLng = v.getTargetLng() - v.getCurrentLng();
            double dist = Math.sqrt(dLat * dLat + dLng * dLng);

            if (dist <= ARRIVAL_THRESHOLD) {
                // Arrived
                handleArrival(v);
            } else {
                // Move one step toward target
                double ratio = STEP / dist;
                v.setCurrentLat(v.getCurrentLat() + dLat * ratio);
                v.setCurrentLng(v.getCurrentLng() + dLng * ratio);

                // Transition DISPATCHED → IN_PROGRESS once within ~500m
                if ("DISPATCHED".equals(v.getVehicleStatus()) && dist < 0.005) {
                    v.setVehicleStatus("IN_PROGRESS");
                    updateEmergencyStatus(v.getAssignedEmergencyId(), "IN_PROGRESS");
                }

                vehicleRepo.save(v);
                anyMoved = true;
            }
        }

        if (anyMoved) broadcastAll();
    }

    private void handleArrival(Vehicle v) {
        // Snap to exact target
        v.setCurrentLat(v.getTargetLat());
        v.setCurrentLng(v.getTargetLng());
        v.setVehicleStatus("RESOLVED");
        vehicleRepo.save(v);

        // Mark emergency RESOLVED
        updateEmergencyStatus(v.getAssignedEmergencyId(), "RESOLVED");

        // Reset vehicle to IDLE at home after 10 seconds
        new Thread(() -> {
            try { Thread.sleep(10_000); } catch (InterruptedException ignored) {}
            vehicleRepo.findById(v.getId()).ifPresent(fresh -> {
                fresh.setCurrentLat(fresh.getHomeLat());
                fresh.setCurrentLng(fresh.getHomeLng());
                fresh.setVehicleStatus("IDLE");
                fresh.setAssignedEmergencyId(null);
                fresh.setTargetLat(null);
                fresh.setTargetLng(null);
                vehicleRepo.save(fresh);
                broadcastAll();
            });
        }).start();

        broadcastAll();
    }

    private void updateEmergencyStatus(Long emergencyId, String status) {
        if (emergencyId == null) return;
        emergencyRepo.findById(emergencyId).ifPresent(e -> {
            e.setStatus(status);
            emergencyRepo.save(e);
            // Broadcast emergency update so customer page gets status changes
            ws.convertAndSend("/topic/emergencies", e);
            ws.convertAndSend("/topic/track/" + e.getTrackingId(), e);
        });
    }

    private void broadcastAll() {
        ws.convertAndSend("/topic/vehicles", vehicleRepo.findAll());
    }

    // ── Nearest resource of the given type to a lat/lng ───────────────────────
    private double[] nearestResource(double lat, double lng, String type) {
        double bestDist = Double.MAX_VALUE;
        double[] best = {23.2599, 77.4126}; // fallback: city centre

        for (int i = 0; i < RESOURCES.length; i++) {
            if (!RESOURCE_TYPES[i].equals(type)) continue;
            double d = haversine(lat, lng, RESOURCES[i][0], RESOURCES[i][1]);
            if (d < bestDist) { bestDist = d; best = RESOURCES[i]; }
        }
        return best;
    }

    private double haversine(double la1, double ln1, double la2, double ln2) {
        final double R = 6371;
        double dLa = Math.toRadians(la2 - la1), dLn = Math.toRadians(ln2 - ln1);
        double a = Math.sin(dLa/2)*Math.sin(dLa/2)
                 + Math.cos(Math.toRadians(la1))*Math.cos(Math.toRadians(la2))
                 * Math.sin(dLn/2)*Math.sin(dLn/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    public List<Vehicle> getAllVehicles() {
        return vehicleRepo.findAll();
    }
}