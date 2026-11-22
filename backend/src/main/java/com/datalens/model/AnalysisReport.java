package com.datalens.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_reports")
public class AnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long datasetId;

    private String metricCol;
    private String categoryCol;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String reportJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Constructors
    public AnalysisReport() {
        this.createdAt = LocalDateTime.now();
    }

    public AnalysisReport(Long datasetId, String metricCol, String categoryCol, String reportJson) {
        this.datasetId = datasetId;
        this.metricCol = metricCol;
        this.categoryCol = categoryCol;
        this.reportJson = reportJson;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public String getMetricCol() { return metricCol; }
    public void setMetricCol(String metricCol) { this.metricCol = metricCol; }

    public String getCategoryCol() { return categoryCol; }
    public void setCategoryCol(String categoryCol) { this.categoryCol = categoryCol; }

    public String getReportJson() { return reportJson; }
    public void setReportJson(String reportJson) { this.reportJson = reportJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
