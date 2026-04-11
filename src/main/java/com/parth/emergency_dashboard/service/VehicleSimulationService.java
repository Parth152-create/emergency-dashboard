package com.parth.emergency_dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parth.emergency_dashboard.model.Emergency;
import com.parth.emergency_dashboard.model.Vehicle;
import com.parth.emergency_dashboard.repository.EmergencyRepository;
import com.parth.emergency_dashboard.repository.VehicleRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service
public class VehicleSimulationService {

    // ── Bhopal resources — mirrors the frontend RESOURCES array ──────────────
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

    private static String vehicleTypeFor(String emergencyType) {
        if (emergencyType == null) return "AMBULANCE";
        return switch (emergencyType.toUpperCase()) {
            case "FIRE"   -> "FIRE_TRUCK";
            case "POLICE" -> "POLICE_CAR";
            default       -> "AMBULANCE";
        };
    }

    private static String resourceTypeFor(String vehicleType) {
        return switch (vehicleType) {
            case "FIRE_TRUCK" -> "fire";
            case "POLICE_CAR" -> "police";
            default           -> "hospital";
        };
    }

    /**
     * Realistic speed ~60 km/h expressed as degrees per 2-second tick.
     * 60 km/h = 0.03333 km per tick; 1 degree ≈ 111 km → 0.0003 deg/tick.
     * Gives ~2 min travel time across typical Bhopal distances.
     */
    private static final double STEP = 0.0003;
    private static final double ARRIVAL_THRESHOLD = 0.0003; // ~33 metres

    // OSRM public API — free, no key required, OpenStreetMap data
    private static final String OSRM_URL =
        "http://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson";

    private final VehicleRepository vehicleRepo;
    private final EmergencyRepository emergencyRepo;
    private final SimpMessagingTemplate ws;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VehicleSimulationService(VehicleRepository vehicleRepo,
                                     EmergencyRepository emergencyRepo,
                                     SimpMessagingTemplate ws) {
        this.vehicleRepo   = vehicleRepo;
        this.emergencyRepo = emergencyRepo;
        this.ws            = ws;
    }

    // ── Seed 3 idle vehicles on startup ──────────────────────────────────────
    @PostConstruct
    public void seedVehicles() {
        if (vehicleRepo.count() > 0) return;
        createVehicle("Ambulance 1",  "AMBULANCE",   23.2599, 77.4664);
        createVehicle("Fire Truck 1", "FIRE_TRUCK",  23.2338, 77.4340);
        createVehicle("Police Car 1", "POLICE_CAR",  23.2320, 77.4310);
    }

    private void createVehicle(String name, String type, double lat, double lng) {
        Vehicle v = new Vehicle();
        v.setName(name); v.setType(type);
        v.setCurrentLat(lat); v.setCurrentLng(lng);
        v.setHomeLat(lat);    v.setHomeLng(lng);
        v.setVehicleStatus("IDLE");
        vehicleRepo.save(v);
    }

    // ── Dispatch: called by EmergencyService ──────────────────────────────────
    public void dispatchVehicle(Emergency emergency) {
        if (emergency.getLatitude() == null || emergency.getLongitude() == null) return;

        String vehicleType = vehicleTypeFor(emergency.getType());
        List<Vehicle> idle = vehicleRepo.findByTypeAndVehicleStatus(vehicleType, "IDLE");
        if (idle.isEmpty()) idle = vehicleRepo.findByVehicleStatus("IDLE");
        if (idle.isEmpty()) return;

        Vehicle vehicle = idle.get(0);
        String resType = resourceTypeFor(vehicleType);
        double[] nearest = nearestResource(emergency.getLatitude(), emergency.getLongitude(), resType);

        vehicle.setCurrentLat(nearest[0]);
        vehicle.setCurrentLng(nearest[1]);
        vehicle.setHomeLat(nearest[0]);
        vehicle.setHomeLng(nearest[1]);
        vehicle.setTargetLat(emergency.getLatitude());
        vehicle.setTargetLng(emergency.getLongitude());
        vehicle.setAssignedEmergencyId(emergency.getId());
        vehicle.setVehicleStatus("DISPATCHED");

        // ── Fetch real road route from OSRM ──────────────────────────────────
        String geoJson = fetchOsrmRoute(nearest[1], nearest[0],
                                         emergency.getLongitude(), emergency.getLatitude());
        vehicle.setRouteGeoJson(geoJson); // null if OSRM unavailable — frontend falls back gracefully
        // ─────────────────────────────────────────────────────────────────────

        vehicleRepo.save(vehicle);

        emergency.setStatus("DISPATCHED");
        emergencyRepo.save(emergency);

        broadcastAll();
    }

    // ── Tick: moves vehicles every 2 seconds ──────────────────────────────────
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
                handleArrival(v);
            } else {
                double ratio = STEP / dist;
                v.setCurrentLat(v.getCurrentLat() + dLat * ratio);
                v.setCurrentLng(v.getCurrentLng() + dLng * ratio);

                // DISPATCHED → IN_PROGRESS when within ~500m
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
        v.setCurrentLat(v.getTargetLat());
        v.setCurrentLng(v.getTargetLng());
        v.setVehicleStatus("RESOLVED");
        v.setRouteGeoJson(null); // clear route on arrival
        vehicleRepo.save(v);

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
                fresh.setRouteGeoJson(null);
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
            ws.convertAndSend("/topic/emergencies", e);
            ws.convertAndSend("/topic/track/" + e.getTrackingId(), e);
        });
    }

    private void broadcastAll() {
        ws.convertAndSend("/topic/vehicles", vehicleRepo.findAll());
    }

    // ── OSRM route fetch ──────────────────────────────────────────────────────
    /**
     * Calls the free OSRM public API for a driving route.
     * Returns a GeoJSON LineString string, or null on failure.
     * The frontend receives this in the vehicle payload and draws it
     * with L.geoJSON() — no extra API calls needed from the browser.
     *
     * OSRM expects coordinates as lng,lat (not lat,lng).
     */
    private String fetchOsrmRoute(double fromLng, double fromLat,
                                   double toLng,   double toLat) {
        try {
            String url = String.format(OSRM_URL, fromLng, fromLat, toLng, toLat);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("[OSRM] HTTP " + response.statusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode geometry = root.path("routes").path(0).path("geometry");

            if (geometry.isMissingNode()) {
                System.err.println("[OSRM] No route geometry in response");
                return null;
            }

            // Return just the geometry node as a JSON string
            // Frontend wraps it in a GeoJSON feature for L.geoJSON()
            return objectMapper.writeValueAsString(geometry);

        } catch (Exception e) {
            System.err.println("[OSRM] Route fetch failed: " + e.getMessage());
            return null; // app continues without route — vehicle still moves
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private double[] nearestResource(double lat, double lng, String type) {
        double bestDist = Double.MAX_VALUE;
        double[] best = {23.2599, 77.4126};
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

    public List<Vehicle> getAllVehicles() { return vehicleRepo.findAll(); }
}