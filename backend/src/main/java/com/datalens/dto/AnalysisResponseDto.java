package com.datalens.dto;

import java.util.List;
import java.util.Map;

public class AnalysisResponseDto {
    private Long datasetId;
    private String metricCol;
    private String categoryCol;
    private String dateCol;
    private String fromDate;
    private String toDate;
    private String categoryValue;

    // Column type classification
    private List<String> numericColumns;
    private List<String> dateColumns;
    private List<String> categoricalColumns;

    // Statistics per numeric column
    private Map<String, Map<String, Object>> columnStats;

    // Pareto / ABC analysis
    private List<Map<String, Object>> paretoData;
    private Map<String, List<String>> abcClassification;

    // Aggregation data for charts
    private List<Map<String, Object>> aggregatedData;
    private List<Map<String, Object>> trendData;

    // Insights and recommendations
    private List<String> insights;
    private List<String> recommendations;

    private String status;
    private String message;

    // Constructors
    public AnalysisResponseDto() {}

    // Getters and Setters
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public String getMetricCol() { return metricCol; }
    public void setMetricCol(String metricCol) { this.metricCol = metricCol; }

    public String getCategoryCol() { return categoryCol; }
    public void setCategoryCol(String categoryCol) { this.categoryCol = categoryCol; }
    public String getDateCol() { return dateCol; }
    public void setDateCol(String dateCol) { this.dateCol = dateCol; }
    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }
    public String getToDate() { return toDate; }
    public void setToDate(String toDate) { this.toDate = toDate; }
    public String getCategoryValue() { return categoryValue; }
    public void setCategoryValue(String categoryValue) { this.categoryValue = categoryValue; }

    public List<String> getNumericColumns() { return numericColumns; }
    public void setNumericColumns(List<String> numericColumns) { this.numericColumns = numericColumns; }

    public List<String> getDateColumns() { return dateColumns; }
    public void setDateColumns(List<String> dateColumns) { this.dateColumns = dateColumns; }

    public List<String> getCategoricalColumns() { return categoricalColumns; }
    public void setCategoricalColumns(List<String> categoricalColumns) { this.categoricalColumns = categoricalColumns; }

    public Map<String, Map<String, Object>> getColumnStats() { return columnStats; }
    public void setColumnStats(Map<String, Map<String, Object>> columnStats) { this.columnStats = columnStats; }

    public List<Map<String, Object>> getParetoData() { return paretoData; }
    public void setParetoData(List<Map<String, Object>> paretoData) { this.paretoData = paretoData; }

    public Map<String, List<String>> getAbcClassification() { return abcClassification; }
    public void setAbcClassification(Map<String, List<String>> abcClassification) { this.abcClassification = abcClassification; }

    public List<Map<String, Object>> getAggregatedData() { return aggregatedData; }
    public void setAggregatedData(List<Map<String, Object>> aggregatedData) { this.aggregatedData = aggregatedData; }
    public List<Map<String, Object>> getTrendData() { return trendData; }
    public void setTrendData(List<Map<String, Object>> trendData) { this.trendData = trendData; }

    public List<String> getInsights() { return insights; }
    public void setInsights(List<String> insights) { this.insights = insights; }

    public List<String> getRecommendations() { return recommendations; }
    public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
