package com.datalens.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class UploadResponseDto {
    private Long datasetId;
    private String originalFileName;
    private String fileType;
    private String sheetName;
    private String businessContext;
    private String dataGoal;
    private Integer rowCount;
    private Integer columnCount;
    private List<String> columns;
    private List<Map<String, Object>> previewRows;
    private LocalDateTime uploadedAt;
    private String status;
    private String message;

    // Constructors
    public UploadResponseDto() {}

    // Getters and Setters
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }

    public String getBusinessContext() { return businessContext; }
    public void setBusinessContext(String businessContext) { this.businessContext = businessContext; }

    public String getDataGoal() { return dataGoal; }
    public void setDataGoal(String dataGoal) { this.dataGoal = dataGoal; }

    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }

    public Integer getColumnCount() { return columnCount; }
    public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public List<Map<String, Object>> getPreviewRows() { return previewRows; }
    public void setPreviewRows(List<Map<String, Object>> previewRows) { this.previewRows = previewRows; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
