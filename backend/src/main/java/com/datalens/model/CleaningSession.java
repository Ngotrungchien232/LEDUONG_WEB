package com.datalens.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cleaning_sessions")
public class CleaningSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long datasetId;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String rulesAppliedJson;

    private Integer rowsAffected;
    private String status; // IN_PROGRESS, COMPLETED, FAILED

    @Lob
    @Column(columnDefinition = "TEXT")
    private String cleanedDataJson;

    // Constructors
    public CleaningSession() {}

    public CleaningSession(Long datasetId) {
        this.datasetId = datasetId;
        this.startedAt = LocalDateTime.now();
        this.status = "IN_PROGRESS";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getRulesAppliedJson() { return rulesAppliedJson; }
    public void setRulesAppliedJson(String rulesAppliedJson) { this.rulesAppliedJson = rulesAppliedJson; }

    public Integer getRowsAffected() { return rowsAffected; }
    public void setRowsAffected(Integer rowsAffected) { this.rowsAffected = rowsAffected; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCleanedDataJson() { return cleanedDataJson; }
    public void setCleanedDataJson(String cleanedDataJson) { this.cleanedDataJson = cleanedDataJson; }
}
