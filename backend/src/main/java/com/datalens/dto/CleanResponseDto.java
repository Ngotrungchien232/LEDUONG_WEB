package com.datalens.dto;

import java.util.List;
import java.util.Map;

public class CleanResponseDto {
    private Long sessionId;
    private Long datasetId;
    private Integer originalRowCount;
    private Integer cleanedRowCount;
    private Integer rowsRemoved;
    private Integer cellsModified;
    private Integer outliersDetected;
    private List<String> rulesApplied;
    private List<Map<String, Object>> cleanedPreview;
    private String message;
    private String status;

    // Constructors
    public CleanResponseDto() {}

    // Getters and Setters
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public Integer getOriginalRowCount() { return originalRowCount; }
    public void setOriginalRowCount(Integer originalRowCount) { this.originalRowCount = originalRowCount; }

    public Integer getCleanedRowCount() { return cleanedRowCount; }
    public void setCleanedRowCount(Integer cleanedRowCount) { this.cleanedRowCount = cleanedRowCount; }

    public Integer getRowsRemoved() { return rowsRemoved; }
    public void setRowsRemoved(Integer rowsRemoved) { this.rowsRemoved = rowsRemoved; }

    public Integer getCellsModified() { return cellsModified; }
    public void setCellsModified(Integer cellsModified) { this.cellsModified = cellsModified; }

    public Integer getOutliersDetected() { return outliersDetected; }
    public void setOutliersDetected(Integer outliersDetected) { this.outliersDetected = outliersDetected; }

    public List<String> getRulesApplied() { return rulesApplied; }
    public void setRulesApplied(List<String> rulesApplied) { this.rulesApplied = rulesApplied; }

    public List<Map<String, Object>> getCleanedPreview() { return cleanedPreview; }
    public void setCleanedPreview(List<Map<String, Object>> cleanedPreview) { this.cleanedPreview = cleanedPreview; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
