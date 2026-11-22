package com.datalens.service;

import com.datalens.dto.CleanRequestDto;
import com.datalens.dto.CleanResponseDto;
import com.datalens.model.AuditLog;
import com.datalens.model.CleaningSession;
import com.datalens.model.Dataset;
import com.datalens.repository.AuditLogRepository;
import com.datalens.repository.CleaningSessionRepository;
import com.datalens.repository.DatasetRepository;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataCleanerService {

    private static final Logger log = LoggerFactory.getLogger(DataCleanerService.class);

    private final DatasetRepository datasetRepo;
    private final CleaningSessionRepository sessionRepo;
    private final AuditLogRepository auditRepo;
    private final FileParserService parserService;
    private final Gson gson = new Gson();

    public DataCleanerService(DatasetRepository datasetRepo,
                               CleaningSessionRepository sessionRepo,
                               AuditLogRepository auditRepo,
                               FileParserService parserService) {
        this.datasetRepo = datasetRepo;
        this.sessionRepo = sessionRepo;
        this.auditRepo = auditRepo;
        this.parserService = parserService;
    }

    public CleanResponseDto clean(Long datasetId, CleanRequestDto req) {
        Dataset dataset = datasetRepo.findById(datasetId)
                .orElseThrow(() -> new RuntimeException("Dataset not found: " + datasetId));

        String sourceJson = dataset.getSourceDataJson() != null
                ? dataset.getSourceDataJson() : dataset.getRawDataJson();
        if (dataset.getSourceDataJson() == null) {
            dataset.setSourceDataJson(sourceJson);
        }
        List<Map<String, Object>> rows = parserService.fromJson(sourceJson);
        if (rows == null) rows = new ArrayList<>();
        int originalCount = rows.size();

        // Create cleaning session
        CleaningSession session = new CleaningSession(datasetId);
        session = sessionRepo.save(session);
        final Long sessionId = session.getId();

        List<String> rulesApplied = new ArrayList<>();
        List<AuditLog> auditLogs = new ArrayList<>();
        int cellsModified = 0;
        int outliersDetected = 0;

        // Deep copy rows for mutation
        List<Map<String, Object>> cleanedRows = deepCopy(rows);

        // Rule 1: Trim whitespace
        if (req.isTrimWhitespace()) {
            for (int i = 0; i < cleanedRows.size(); i++) {
                Map<String, Object> row = cleanedRows.get(i);
                for (String col : row.keySet()) {
                    Object val = row.get(col);
                    if (val instanceof String) {
                        String trimmed = ((String) val).trim();
                        if (!trimmed.equals(val)) {
                            auditLogs.add(new AuditLog(datasetId, sessionId, i + 1, col,
                                    (String) val, trimmed, "TRIM_WHITESPACE"));
                            row.put(col, trimmed);
                            cellsModified++;
                        }
                    }
                }
            }
            rulesApplied.add("TRIM_WHITESPACE");
        }

        // Rule 2: Remove duplicates
        if (req.isRemoveDuplicates()) {
            int before = cleanedRows.size();
            cleanedRows = removeDuplicates(cleanedRows, datasetId, sessionId, auditLogs);
            int removed = before - cleanedRows.size();
            if (removed > 0) {
                rulesApplied.add("REMOVE_DUPLICATES (" + removed + " rows)");
            } else {
                rulesApplied.add("REMOVE_DUPLICATES");
            }
        }

        // Rule 3: Standardize text (capitalize first letter of string fields)
        if (req.isStandardizeText()) {
            for (int i = 0; i < cleanedRows.size(); i++) {
                Map<String, Object> row = cleanedRows.get(i);
                for (String col : row.keySet()) {
                    Object val = row.get(col);
                    if (val instanceof String && !((String) val).isEmpty()) {
                        String std = standardizeText((String) val);
                        if (!std.equals(val)) {
                            auditLogs.add(new AuditLog(datasetId, sessionId, i + 1, col,
                                    (String) val, std, "STANDARDIZE_TEXT"));
                            row.put(col, std);
                            cellsModified++;
                        }
                    }
                }
            }
            rulesApplied.add("STANDARDIZE_TEXT");
        }

        // Identify numeric columns
        List<String> numericCols = identifyNumericColumns(cleanedRows);

        // Rule 4: Fill missing with mean
        if (req.isFillMissingWithMean()) {
            for (String col : numericCols) {
                List<Double> vals = extractNumericValues(cleanedRows, col);
                if (vals.isEmpty()) continue;
                double mean = vals.stream().mapToDouble(d -> d).average().orElse(0);
                for (int i = 0; i < cleanedRows.size(); i++) {
                    Object v = cleanedRows.get(i).get(col);
                    if (v == null || v.toString().trim().isEmpty()) {
                        String before = v == null ? "null" : v.toString();
                        cleanedRows.get(i).put(col, mean);
                        auditLogs.add(new AuditLog(datasetId, sessionId, i + 1, col,
                                before, String.valueOf(mean), "FILL_MISSING_MEAN"));
                        cellsModified++;
                    }
                }
            }
            rulesApplied.add("FILL_MISSING_WITH_MEAN");
        }

        // Rule 5: Fill missing with median
        if (req.isFillMissingWithMedian()) {
            for (String col : numericCols) {
                List<Double> vals = extractNumericValues(cleanedRows, col);
                if (vals.isEmpty()) continue;
                double median = computeMedian(vals);
                for (int i = 0; i < cleanedRows.size(); i++) {
                    Object v = cleanedRows.get(i).get(col);
                    if (v == null || v.toString().trim().isEmpty()) {
                        String before = v == null ? "null" : v.toString();
                        cleanedRows.get(i).put(col, median);
                        auditLogs.add(new AuditLog(datasetId, sessionId, i + 1, col,
                                before, String.valueOf(median), "FILL_MISSING_MEDIAN"));
                        cellsModified++;
                    }
                }
            }
            rulesApplied.add("FILL_MISSING_WITH_MEDIAN");
        }

        // Rule 6: Remove rows with missing values
        if (req.isRemoveMissingRows()) {
            int before = cleanedRows.size();
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (int i = 0; i < cleanedRows.size(); i++) {
                Map<String, Object> row = cleanedRows.get(i);
                boolean hasMissing = row.values().stream()
                        .anyMatch(v -> v == null || v.toString().trim().isEmpty());
                if (hasMissing) {
                    auditLogs.add(new AuditLog(datasetId, sessionId, i + 1, "*",
                            "ROW_EXISTS", "REMOVED", "REMOVE_MISSING_ROWS"));
                } else {
                    filtered.add(row);
                }
            }
            cleanedRows = filtered;
            int removed = before - cleanedRows.size();
            rulesApplied.add("REMOVE_MISSING_ROWS (" + removed + " rows)");
        }

        // Rule 7 (extra): Detect outliers with IQR
        if (req.isDetectOutliers()) {
            for (String col : numericCols) {
                List<Double> vals = extractNumericValues(cleanedRows, col);
                if (vals.size() < 4) continue;

                Collections.sort(vals);
                double q1 = percentile(vals, 25);
                double q3 = percentile(vals, 75);
                double iqr = q3 - q1;
                double lower = q1 - 1.5 * iqr;
                double upper = q3 + 1.5 * iqr;

                for (int i = 0; i < cleanedRows.size(); i++) {
                    Object v = cleanedRows.get(i).get(col);
                    if (v == null) continue;
                    Double numVal = toDouble(v);
                    if (numVal != null && (numVal < lower || numVal > upper)) {
                        // Mark as outlier (don't remove)
                        String outlierKey = "__outlier_" + col;
                        cleanedRows.get(i).put(outlierKey, true);
                        auditLogs.add(new AuditLog(datasetId, sessionId, i + 1, col,
                                v.toString(), v.toString(),
                                "OUTLIER_DETECTED [IQR: " + String.format("%.2f", lower) + " - " + String.format("%.2f", upper) + "]"));
                        outliersDetected++;
                    }
                }
            }
            rulesApplied.add("DETECT_OUTLIERS_IQR (" + outliersDetected + " outliers)");
        }

        // Save audit logs in batch
        if (!auditLogs.isEmpty()) {
            auditRepo.saveAll(auditLogs);
        }

        // Update session
        session.setCompletedAt(LocalDateTime.now());
        session.setStatus("COMPLETED");
        session.setRowsAffected(originalCount - cleanedRows.size() + cellsModified);
        session.setRulesAppliedJson(gson.toJson(rulesApplied));
        session.setCleanedDataJson(parserService.toJson(cleanedRows));
        sessionRepo.save(session);

        // Update dataset with cleaned data and status
        dataset.setStatus("CLEANED");
        dataset.setRawDataJson(parserService.toJson(cleanedRows));
        dataset.setRowCount(cleanedRows.size());
        datasetRepo.save(dataset);

        // Build response
        CleanResponseDto resp = new CleanResponseDto();
        resp.setSessionId(session.getId());
        resp.setDatasetId(datasetId);
        resp.setOriginalRowCount(originalCount);
        resp.setCleanedRowCount(cleanedRows.size());
        resp.setRowsRemoved(originalCount - cleanedRows.size());
        resp.setCellsModified(cellsModified);
        resp.setOutliersDetected(outliersDetected);
        resp.setRulesApplied(rulesApplied);
        resp.setStatus("COMPLETED");
        resp.setMessage("Data cleaning completed successfully");

        // Preview (first 10 rows, without outlier markers)
        List<Map<String, Object>> preview = cleanedRows.stream()
                .limit(10)
                .map(row -> {
                    Map<String, Object> clean = new LinkedHashMap<>();
                    row.forEach((k, v) -> { if (!k.startsWith("__")) clean.put(k, v); });
                    return clean;
                })
                .collect(Collectors.toList());
        resp.setCleanedPreview(preview);

        return resp;
    }

    private List<Map<String, Object>> removeDuplicates(List<Map<String, Object>> rows,
                                                        Long datasetId, Long sessionId,
                                                        List<AuditLog> auditLogs) {
        List<Map<String, Object>> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            String key = rows.get(i).toString();
            if (!seen.contains(key)) {
                seen.add(key);
                unique.add(rows.get(i));
            } else {
                auditLogs.add(new AuditLog(datasetId, sessionId, i + 1, "*",
                        "ROW_EXISTS", "REMOVED", "REMOVE_DUPLICATE"));
            }
        }
        return unique;
    }

    private String standardizeText(String val) {
        if (val == null || val.isEmpty()) return val;
        return Character.toUpperCase(val.charAt(0)) + val.substring(1);
    }

    private List<String> identifyNumericColumns(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return Collections.emptyList();
        Map<String, Object> first = rows.get(0);
        List<String> numCols = new ArrayList<>();
        for (String col : first.keySet()) {
            if (col.startsWith("__")) continue;
            long numericCount = rows.stream()
                    .map(r -> r.get(col))
                    .filter(v -> toDouble(v) != null)
                    .count();
            if ((double) numericCount / rows.size() > 0.6) {
                numCols.add(col);
            }
        }
        return numCols;
    }

    private List<Double> extractNumericValues(List<Map<String, Object>> rows, String col) {
        return rows.stream()
                .map(r -> toDouble(r.get(col)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    private double computeMedian(List<Double> sorted) {
        List<Double> s = new ArrayList<>(sorted);
        Collections.sort(s);
        int n = s.size();
        return n % 2 == 0 ? (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0 : s.get(n / 2);
    }

    private double percentile(List<Double> sorted, double p) {
        double index = (p / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return sorted.get(lower);
        return sorted.get(lower) + (index - lower) * (sorted.get(upper) - sorted.get(lower));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> deepCopy(List<Map<String, Object>> original) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> row : original) {
            copy.add(new LinkedHashMap<>(row));
        }
        return copy;
    }
}
