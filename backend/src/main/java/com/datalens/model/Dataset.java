package com.datalens.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "datasets")
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    private Integer rowCount;
    private Integer columnCount;
    private String sheetName;
    private String fileType;
    @Column(length = 500)
    private String businessContext;
    @Column(length = 1000)
    private String dataGoal;

    @Column(nullable = false)
    private String status = "UPLOADED"; // UPLOADED, CLEANING, CLEANED, ANALYZED

    @Lob
    @Column(columnDefinition = "TEXT")
    private String rawDataJson;

    // Immutable upload snapshot so changing cleaning rules always starts from the
    // same source data instead of progressively modifying an earlier result.
    @Lob
    @Column(columnDefinition = "TEXT")
    private String sourceDataJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String columnNamesJson;

    // Constructors
    public Dataset() {}

    public Dataset(String originalFileName, LocalDateTime uploadedAt) {
        this.originalFileName = originalFileName;
        this.uploadedAt = uploadedAt;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }

    public Integer getColumnCount() { return columnCount; }
    public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }

    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getBusinessContext() { return businessContext; }
    public void setBusinessContext(String businessContext) { this.businessContext = businessContext; }

    public String getDataGoal() { return dataGoal; }
    public void setDataGoal(String dataGoal) { this.dataGoal = dataGoal; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRawDataJson() { return rawDataJson; }
    public void setRawDataJson(String rawDataJson) { this.rawDataJson = rawDataJson; }

    public String getSourceDataJson() { return sourceDataJson; }
    public void setSourceDataJson(String sourceDataJson) { this.sourceDataJson = sourceDataJson; }

    public String getColumnNamesJson() { return columnNamesJson; }
    public void setColumnNamesJson(String columnNamesJson) { this.columnNamesJson = columnNamesJson; }
}
