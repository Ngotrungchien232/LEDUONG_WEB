package com.datalens.dto;

public class CleanRequestDto {
    // Rule flags
    private boolean removeDuplicates = true;
    private boolean trimWhitespace = true;
    private boolean fillMissingWithMean = false;
    private boolean fillMissingWithMedian = false;
    private boolean removeMissingRows = false;
    private boolean detectOutliers = true;
    private boolean standardizeText = false;
    private boolean normalizeNumbers = false;

    // Optional: specific columns to apply rules
    private java.util.List<String> targetColumns;

    // Constructors
    public CleanRequestDto() {}

    // Getters and Setters
    public boolean isRemoveDuplicates() { return removeDuplicates; }
    public void setRemoveDuplicates(boolean removeDuplicates) { this.removeDuplicates = removeDuplicates; }

    public boolean isTrimWhitespace() { return trimWhitespace; }
    public void setTrimWhitespace(boolean trimWhitespace) { this.trimWhitespace = trimWhitespace; }

    public boolean isFillMissingWithMean() { return fillMissingWithMean; }
    public void setFillMissingWithMean(boolean fillMissingWithMean) { this.fillMissingWithMean = fillMissingWithMean; }

    public boolean isFillMissingWithMedian() { return fillMissingWithMedian; }
    public void setFillMissingWithMedian(boolean fillMissingWithMedian) { this.fillMissingWithMedian = fillMissingWithMedian; }

    public boolean isRemoveMissingRows() { return removeMissingRows; }
    public void setRemoveMissingRows(boolean removeMissingRows) { this.removeMissingRows = removeMissingRows; }

    public boolean isDetectOutliers() { return detectOutliers; }
    public void setDetectOutliers(boolean detectOutliers) { this.detectOutliers = detectOutliers; }

    public boolean isStandardizeText() { return standardizeText; }
    public void setStandardizeText(boolean standardizeText) { this.standardizeText = standardizeText; }

    public boolean isNormalizeNumbers() { return normalizeNumbers; }
    public void setNormalizeNumbers(boolean normalizeNumbers) { this.normalizeNumbers = normalizeNumbers; }

    public java.util.List<String> getTargetColumns() { return targetColumns; }
    public void setTargetColumns(java.util.List<String> targetColumns) { this.targetColumns = targetColumns; }
}
