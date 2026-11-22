package com.datalens.service;

import com.datalens.dto.AnalysisResponseDto;
import com.datalens.model.AnalysisReport;
import com.datalens.model.Dataset;
import com.datalens.repository.AnalysisReportRepository;
import com.datalens.repository.DatasetRepository;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final DatasetRepository datasetRepo;
    private final AnalysisReportRepository reportRepo;
    private final FileParserService parserService;
    private final Gson gson = new Gson();

    public AnalyticsService(DatasetRepository datasetRepo,
                             AnalysisReportRepository reportRepo,
                             FileParserService parserService) {
        this.datasetRepo = datasetRepo;
        this.reportRepo = reportRepo;
        this.parserService = parserService;
    }

    public AnalysisResponseDto analyze(Long datasetId, String metricCol, String categoryCol) {
        return analyze(datasetId, metricCol, categoryCol, null, null, null, null);
    }

    public AnalysisResponseDto analyze(Long datasetId, String metricCol, String categoryCol,
                                       String dateCol, String fromDate, String toDate, String categoryValue) {
        Dataset dataset = datasetRepo.findById(datasetId)
                .orElseThrow(() -> new RuntimeException("Dataset not found: " + datasetId));

        List<Map<String, Object>> rows = parserService.fromJson(dataset.getRawDataJson());
        if (rows == null) rows = new ArrayList<>();

        AnalysisResponseDto resp = new AnalysisResponseDto();
        resp.setDatasetId(datasetId);
        resp.setMetricCol(metricCol);
        resp.setCategoryCol(categoryCol);

        // Classify columns
        List<String> numericCols = new ArrayList<>();
        List<String> dateCols = new ArrayList<>();
        List<String> catCols = new ArrayList<>();

        if (!rows.isEmpty()) {
            for (String col : rows.get(0).keySet()) {
                if (col.startsWith("__")) continue;
                String type = classifyColumn(rows, col);
                if ("NUMERIC".equals(type)) numericCols.add(col);
                else if ("DATE".equals(type)) dateCols.add(col);
                else catCols.add(col);
            }
        }

        resp.setNumericColumns(numericCols);
        resp.setDateColumns(dateCols);
        resp.setCategoricalColumns(catCols);

        if (dateCol != null && !dateCol.isBlank()) {
            if (!dateCols.contains(dateCol)) throw new IllegalArgumentException("Cột ngày không hợp lệ: " + dateCol);
            LocalDate from = parseDate(fromDate);
            LocalDate to = parseDate(toDate);
            rows = rows.stream().filter(row -> {
                LocalDate date = parseDate(Objects.toString(row.get(dateCol), null));
                return date != null && (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
            }).collect(Collectors.toList());
        }
        if (categoryValue != null && !categoryValue.isBlank()) {
            String filterColumn = categoryCol;
            if (filterColumn == null || filterColumn.isBlank() ||
                    (!catCols.contains(filterColumn) && !dateCols.contains(filterColumn))) {
                throw new IllegalArgumentException("Hãy chọn cột phân loại hợp lệ trước khi lọc giá trị.");
            }
            rows = rows.stream().filter(row -> Objects.equals(
                    Objects.toString(row.get(filterColumn), ""), categoryValue)).collect(Collectors.toList());
        }

        // Statistics per numeric column
        Map<String, Map<String, Object>> columnStats = new LinkedHashMap<>();
        for (String col : numericCols) {
            columnStats.put(col, computeStats(rows, col));
        }
        resp.setColumnStats(columnStats);

        // Aggregation and Pareto
        String effectiveMetric = (metricCol != null && !metricCol.isEmpty()) ? metricCol :
                (numericCols.isEmpty() ? null : numericCols.get(0));
        String effectiveCategory = (categoryCol != null && !categoryCol.isEmpty()) ? categoryCol :
                (!catCols.isEmpty() ? catCols.get(0) : (dateCols.isEmpty() ? null : dateCols.get(0)));

        if (effectiveMetric != null && !numericCols.contains(effectiveMetric)) {
            throw new IllegalArgumentException("Cột chỉ số không hợp lệ hoặc không phải dữ liệu số: " + effectiveMetric);
        }
        if (effectiveCategory != null && !catCols.contains(effectiveCategory) && !dateCols.contains(effectiveCategory)) {
            throw new IllegalArgumentException("Cột phân loại không hợp lệ: " + effectiveCategory);
        }
        // Return the selected defaults as well as explicit selections so the UI and
        // exported AI context use the same metric/category as the calculations.
        resp.setMetricCol(effectiveMetric);
        resp.setCategoryCol(effectiveCategory);
        resp.setDateCol(dateCol);
        resp.setFromDate(fromDate);
        resp.setToDate(toDate);
        resp.setCategoryValue(categoryValue);

        if (effectiveMetric != null && effectiveCategory != null) {
            List<Map<String, Object>> aggregated = aggregateData(rows, effectiveMetric, effectiveCategory);
            resp.setAggregatedData(aggregated);
            List<Map<String, Object>> trend = new ArrayList<>(aggregated);
            if (dateCols.contains(effectiveCategory)) {
                trend.sort(Comparator.comparing(item -> parseDate(Objects.toString(item.get("category"), "")),
                        Comparator.nullsLast(Comparator.naturalOrder())));
            } else {
                trend.clear();
            }
            resp.setTrendData(trend);

            // Pareto
            List<Map<String, Object>> pareto = computePareto(aggregated, effectiveMetric, effectiveCategory);
            resp.setParetoData(pareto);

            // ABC classification
            resp.setAbcClassification(computeABC(pareto, effectiveCategory));
        }

        // Insights and recommendations
        resp.setInsights(generateInsights(rows, numericCols, columnStats, effectiveMetric, effectiveCategory));
        resp.setRecommendations(generateRecommendations(rows, numericCols, columnStats));

        resp.setStatus("SUCCESS");
        resp.setMessage("Phân tích dữ liệu hoàn tất thành công");

        // Persist report
        String reportJson = gson.toJson(resp);
        AnalysisReport report = new AnalysisReport(datasetId, effectiveMetric, effectiveCategory, reportJson);
        reportRepo.save(report);

        // Update dataset status
        dataset.setStatus("ANALYZED");
        datasetRepo.save(dataset);

        return resp;
    }

    private String classifyColumn(List<Map<String, Object>> rows, String col) {
        long total = rows.size();
        if (total == 0) return "CATEGORICAL";

        long numericCount = rows.stream()
                .map(r -> r.get(col))
                .filter(v -> toDouble(v) != null)
                .count();
        if ((double) numericCount / total > 0.6) return "NUMERIC";

        long dateCount = rows.stream()
                .map(r -> r.get(col))
                .filter(v -> v != null && isDateString(v.toString()))
                .count();
        if ((double) dateCount / total > 0.6) return "DATE";

        return "CATEGORICAL";
    }

    private boolean isDateString(String s) {
        return s.matches("\\d{4}-\\d{2}-\\d{2}.*") ||
               s.matches("\\d{2}/\\d{2}/\\d{4}.*") ||
               s.matches("\\d{2}-\\d{2}-\\d{4}.*");
    }

    private Map<String, Object> computeStats(List<Map<String, Object>> rows, String col) {
        List<Double> vals = rows.stream()
                .map(r -> toDouble(r.get(col)))
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        Map<String, Object> stats = new LinkedHashMap<>();
        if (vals.isEmpty()) {
            stats.put("count", 0);
            stats.put("missing", rows.size());
            return stats;
        }

        int n = vals.size();
        double sum = vals.stream().mapToDouble(d -> d).sum();
        double mean = sum / n;
        double median = n % 2 == 0 ? (vals.get(n / 2 - 1) + vals.get(n / 2)) / 2.0 : vals.get(n / 2);
        double variance = vals.stream().mapToDouble(d -> (d - mean) * (d - mean)).sum() / n;
        double stdDev = Math.sqrt(variance);

        stats.put("count", n);
        stats.put("missing", rows.size() - n);
        stats.put("sum", roundTo2(sum));
        stats.put("mean", roundTo2(mean));
        stats.put("median", roundTo2(median));
        stats.put("stdDev", roundTo2(stdDev));
        stats.put("min", roundTo2(vals.get(0)));
        stats.put("max", roundTo2(vals.get(n - 1)));
        stats.put("q1", roundTo2(percentile(vals, 25)));
        stats.put("q3", roundTo2(percentile(vals, 75)));
        stats.put("iqr", roundTo2(percentile(vals, 75) - percentile(vals, 25)));

        return stats;
    }

    private List<Map<String, Object>> aggregateData(List<Map<String, Object>> rows,
                                                     String metricCol, String categoryCol) {
        Map<String, Double> aggregated = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object catVal = row.get(categoryCol);
            Object metricVal = row.get(metricCol);
            String cat = catVal != null ? catVal.toString() : "Unknown";
            Double val = toDouble(metricVal);
            if (val != null) {
                aggregated.merge(cat, val, Double::sum);
            }
        }

        return aggregated.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", e.getKey());
                    m.put("value", roundTo2(e.getValue()));
                    return m;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> computePareto(List<Map<String, Object>> sorted,
                                                      String metricCol, String categoryCol) {
        sorted = new ArrayList<>(sorted);
        sorted.sort(Comparator.comparingDouble((Map<String, Object> item) -> toDoubleObj(item.get("value"))).reversed());
        double total = sorted.stream()
                .mapToDouble(m -> toDoubleObj(m.get("value")))
                .sum();
        if (total == 0) return sorted;

        double cumulative = 0;
        List<Map<String, Object>> pareto = new ArrayList<>();
        for (Map<String, Object> item : sorted) {
            cumulative += toDoubleObj(item.get("value"));
            Map<String, Object> p = new LinkedHashMap<>(item);
            p.put("cumulativePct", roundTo2(cumulative / total * 100));
            pareto.add(p);
        }
        return pareto;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.length() >= 10 && text.charAt(4) == '-') text = text.substring(0, 10);
        else if (text.length() >= 10 && (text.charAt(2) == '/' || text.charAt(2) == '-')) text = text.substring(0, 10);
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"))) {
            try { return LocalDate.parse(text, formatter); } catch (Exception ignored) { }
        }
        return null;
    }

    private Map<String, List<String>> computeABC(List<Map<String, Object>> pareto, String categoryCol) {
        List<String> a = new ArrayList<>(), b = new ArrayList<>(), c = new ArrayList<>();
        for (Map<String, Object> item : pareto) {
            double pct = toDoubleObj(item.get("cumulativePct"));
            String cat = item.get("category") != null ? item.get("category").toString() : "?";
            if (pct <= 80) a.add(cat);
            else if (pct <= 95) b.add(cat);
            else c.add(cat);
        }
        Map<String, List<String>> abc = new LinkedHashMap<>();
        abc.put("A", a);
        abc.put("B", b);
        abc.put("C", c);
        return abc;
    }

    private List<String> generateInsights(List<Map<String, Object>> rows, List<String> numericCols,
                                           Map<String, Map<String, Object>> columnStats,
                                           String metricCol, String categoryCol) {
        List<String> insights = new ArrayList<>();

        insights.add(String.format("Tập dữ liệu ghi nhận %d dòng và %d cột thông tin sẵn sàng phân tích.",
                rows.size(), rows.isEmpty() ? 0 : rows.get(0).size()));

        for (String col : numericCols) {
            if (insights.size() >= 4) break;
            Map<String, Object> stats = columnStats.get(col);
            if (stats == null) continue;

            Number mean = (Number) stats.get("mean");
            Number stdDev = (Number) stats.get("stdDev");
            Number max = (Number) stats.get("max");
            Number min = (Number) stats.get("min");

            if (mean != null && stdDev != null && mean.doubleValue() != 0) {
                double cv = stdDev.doubleValue() / Math.abs(mean.doubleValue()) * 100;
                if (cv > 50) {
                    insights.add(String.format("Chỉ số '%s' có độ biến thiên cao (CV = %.1f%%), phản ánh mức độ phân tán lớn giữa các nhóm.", col, cv));
                } else {
                    insights.add(String.format("Chỉ số '%s' có giá trị trung bình = %.2f, độ lệch chuẩn = %.2f (CV = %.1f%% ở ngưỡng ổn định).",
                            col, mean.doubleValue(), stdDev.doubleValue(), cv));
                }
            }

            if (max != null && min != null) {
                double range = max.doubleValue() - min.doubleValue();
                if (range > 0) {
                    insights.add(String.format("Khoảng biến thiên của '%s' dao động từ %.2f đến %.2f (biên độ %.2f).",
                            col, min.doubleValue(), max.doubleValue(), range));
                }
            }
        }

        // Check missing data
        long missingTotal = columnStats.values().stream()
                .mapToLong(s -> s.containsKey("missing") ? ((Number) s.get("missing")).longValue() : 0)
                .sum();
        if (missingTotal > 0) {
            insights.add(String.format("Phát hiện %d ô dữ liệu trống ở các cột số học có thể ảnh hưởng đến độ chuẩn xác thống kê.", missingTotal));
        }

        return insights.stream().limit(4).collect(Collectors.toList());
    }

    private List<String> generateRecommendations(List<Map<String, Object>> rows,
                                                   List<String> numericCols,
                                                   Map<String, Map<String, Object>> columnStats) {
        List<String> recs = new ArrayList<>();

        // Check for missing data
        boolean hasMissing = columnStats.values().stream()
                .anyMatch(s -> s.containsKey("missing") && ((Number) s.get("missing")).intValue() > 0);
        if (hasMissing) {
            recs.add("Nên áp dụng quy tắc làm sạch để điền khuyết giá trị trung bình hoặc loại bỏ ô trống trước khi phân tích sâu.");
        }

        // Check for high variability
        for (String col : numericCols) {
            Map<String, Object> stats = columnStats.get(col);
            if (stats == null) continue;
            Number mean = (Number) stats.get("mean");
            Number stdDev = (Number) stats.get("stdDev");
            if (mean != null && stdDev != null && mean.doubleValue() != 0) {
                double cv = stdDev.doubleValue() / Math.abs(mean.doubleValue()) * 100;
                if (cv > 100) {
                    recs.add(String.format("Cột '%s' có độ biến động rất lớn (CV > 100%%). Khuyến nghị kiểm tra ngoại lai IQR để loại trừ sai số.", col));
                    break;
                }
            }
        }

        if (rows.size() < 30) {
            recs.add("Dung lượng mẫu tương đối nhỏ (" + rows.size() + " dòng). Nên bổ sung thêm dữ liệu để tăng độ tin cậy thống kê.");
        }

        if (recs.isEmpty()) {
            recs.add("Chất lượng dữ liệu đạt chuẩn. Có thể tiến hành phân tích tương quan và trích xuất báo cáo quản trị.");
        }

        return recs.stream().limit(3).collect(Collectors.toList());
    }

    private Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    private double toDoubleObj(Object v) {
        Double d = toDouble(v);
        return d == null ? 0.0 : d;
    }

    private double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        double index = (p / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return sorted.get(lower);
        return sorted.get(lower) + (index - lower) * (sorted.get(upper) - sorted.get(lower));
    }

    private double roundTo2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}
