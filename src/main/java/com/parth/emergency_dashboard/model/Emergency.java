package com.parth.emergency_dashboard.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "emergencies")
public class Emergency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Type is required")
    private String type;

    @NotBlank(message = "Location is required")
    private String location;

    @NotBlank(message = "Status is required")
    private String status;

    private Double latitude;
    private Double longitude;

    // ── REPORTER FIELDS ──────────────────────────────
    private String reporterName;
    private String reporterPhone;

    @Column(unique = true)
    private String trackingId;  // e.g. "TRK-A3F9X2"

    // ── AI CLASSIFICATION FIELDS ─────────────────────
    /**
     * Free-text description from the reporter.
     * Used by AiClassificationService to determine priority.
     * Stored as TEXT to support longer descriptions.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Set automatically by AI. Manual override allowed by admin. */
    private String priority; // HIGH / MEDIUM / LOW

    /** Whether priority was set by AI (true) or manually overridden (false) */
    private boolean aiClassified = false;
    // ─────────────────────────────────────────────────

    public Emergency() {}

    // ── Getters & Setters ────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getReporterPhone() { return reporterPhone; }
    public void setReporterPhone(String reporterPhone) { this.reporterPhone = reporterPhone; }

    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public boolean isAiClassified() { return aiClassified; }
    public void setAiClassified(boolean aiClassified) { this.aiClassified = aiClassified; }
}