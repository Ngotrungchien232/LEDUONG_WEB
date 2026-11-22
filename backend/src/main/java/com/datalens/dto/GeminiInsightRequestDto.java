package com.datalens.dto;

import java.util.List;
import java.util.Map;

public class GeminiInsightRequestDto {
    private Long datasetId;
    private String apiKey;
    private String metricCol;
    private String categoryCol;
    private String analysisContext;
    private Map<String, Object> statistics;
    private List<String> columns;
    private Integer rowCount;
    private String userQuestion;

    // Constructors
    public GeminiInsightRequestDto() {}

    // Getters and Setters
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getMetricCol() { return metricCol; }
    public void setMetricCol(String metricCol) { this.metricCol = metricCol; }

    public String getCategoryCol() { return categoryCol; }
    public void setCategoryCol(String categoryCol) { this.categoryCol = categoryCol; }

    public String getAnalysisContext() { return analysisContext; }
    public void setAnalysisContext(String analysisContext) { this.analysisContext = analysisContext; }

    public Map<String, Object> getStatistics() { return statistics; }
    public void setStatistics(Map<String, Object> statistics) { this.statistics = statistics; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }

    public String getUserQuestion() { return userQuestion; }
    public void setUserQuestion(String userQuestion) { this.userQuestion = userQuestion; }
}
