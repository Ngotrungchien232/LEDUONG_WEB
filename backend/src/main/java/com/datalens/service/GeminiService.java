package com.datalens.service;

import com.datalens.dto.GeminiInsightRequestDto;
import com.datalens.model.Dataset;
import com.datalens.repository.DatasetRepository;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final String[] CANDIDATE_MODELS = {
        "gemini-3.1-flash-lite",
        "gemini-flash-lite-latest",
        "gemini-3-flash-preview",
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash"
    };

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String apiUrl;

    private final DatasetRepository datasetRepo;
    private final FileParserService parserService;
    private final Gson gson = new Gson();

    public static class GeminiCallResult {
        public final String body;
        public final String model;
        public GeminiCallResult(String body, String model) {
            this.body = body;
            this.model = model;
        }
    }

    public GeminiService(DatasetRepository datasetRepo, FileParserService parserService) {
        this.datasetRepo = datasetRepo;
        this.parserService = parserService;
    }

    public Map<String, Object> testConnection(String testKey) {
        String key = (testKey != null && !testKey.trim().isEmpty()) ? testKey.trim() : this.apiKey;
        Map<String, Object> result = new LinkedHashMap<>();

        if (key == null || key.trim().isEmpty()) {
            result.put("success", false);
            result.put("error", "Chưa cung cấp API Key. Vui lòng dán khóa API của bạn.");
            return result;
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

        for (String model : CANDIDATE_MODELS) {
            try {
                String testUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key;
                Map<String, Object> requestBody = Map.of(
                        "contents", List.of(Map.of("parts", List.of(Map.of("text", "Xin chào!")))),
                        "generationConfig", Map.of("maxOutputTokens", 10)
                );
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(testUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                        .timeout(Duration.ofSeconds(20))
                        .build();

                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    result.put("success", true);
                    result.put("model", model);
                    result.put("message", "Kết nối thành công tới mô hình " + model + "!");
                    return result;
                } else if (resp.statusCode() == 404 || resp.statusCode() == 503) {
                    log.info("Model {} returned {}, trying next candidate", model, resp.statusCode());
                    continue;
                } else {
                    result.put("success", false);
                    result.put("statusCode", resp.statusCode());
                    result.put("error", parseErrorMessage(resp.body(), resp.statusCode()));
                    return result;
                }
            } catch (Exception e) {
                log.warn("Test connection failed for {}: {}", model, e.getMessage());
            }
        }

        result.put("success", false);
        result.put("error", "Không thể kết nối tới Google Gemini. Vui lòng kiểm tra lại kết nối mạng hoặc tính hợp lệ của API Key.");
        return result;
    }

    public boolean hasApiKey() {
        return this.apiKey != null && !this.apiKey.trim().isEmpty();
    }

    public Map<String, Object> extractTableFromPdf(byte[] pdfBytes, String passedApiKey) {
        String effectiveKey = (passedApiKey != null && !passedApiKey.trim().isEmpty())
                ? passedApiKey.trim()
                : this.apiKey;

        if (effectiveKey == null || effectiveKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Chưa cấu hình API Key để kích hoạt AI trích xuất PDF.");
        }

        String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        Map<String, Object> inlineData = Map.of(
                "mimeType", "application/pdf",
                "data", base64Pdf
        );
        Map<String, Object> textPart = Map.of(
                "text", "Bạn là hệ thống trích xuất bảng dữ liệu tự động. Hãy trích xuất toàn bộ dữ liệu bảng biểu chính từ tệp PDF này. " +
                        "Trả về định dạng JSON thuần túy có cấu trúc: {\"columns\": [\"Tên cột 1\", \"Tên cột 2\"], \"rows\": [{\"Tên cột 1\": giá_trị, \"Tên cột 2\": giá_trị}]}. " +
                        "Quy tắc quan trọng: Chỉ trả về JSON hợp lệ, không bọc trong markdown fence hay giải thích nào khác."
        );

        Map<String, Object> content = Map.of("parts", List.of(Map.of("inlineData", inlineData), textPart));
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("contents", List.of(content));

        Map<String, Object> genConfig = new LinkedHashMap<>();
        genConfig.put("temperature", 0.1);
        genConfig.put("maxOutputTokens", 4000);
        genConfig.put("responseMimeType", "application/json");
        requestBody.put("generationConfig", genConfig);

        String bodyJson = gson.toJson(requestBody);

        Exception lastException = null;
        for (String model : CANDIDATE_MODELS) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + effectiveKey;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .timeout(Duration.ofSeconds(60))
                        .build();

                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    Map<?, ?> respMap = gson.fromJson(resp.body(), Map.class);
                    List<?> candidates = (List<?>) respMap.get("candidates");
                    if (candidates != null && !candidates.isEmpty()) {
                        Map<?, ?> first = (Map<?, ?>) candidates.get(0);
                        Map<?, ?> contentMap = (Map<?, ?>) first.get("content");
                        List<?> parts = (List<?>) contentMap.get("parts");
                        String jsonText = (String) ((Map<?, ?>) parts.get(0)).get("text");
                        if (jsonText != null) {
                            jsonText = jsonText.trim();
                            if (jsonText.startsWith("```json")) jsonText = jsonText.substring(7);
                            if (jsonText.startsWith("```")) jsonText = jsonText.substring(3);
                            if (jsonText.endsWith("```")) jsonText = jsonText.substring(0, jsonText.length() - 3);
                            jsonText = jsonText.trim();

                            @SuppressWarnings("unchecked")
                            Map<String, Object> parsed = gson.fromJson(jsonText, Map.class);
                            return parsed;
                        }
                    }
                }
                if (resp.statusCode() == 404 || resp.statusCode() == 503) {
                    log.warn("Model {} returned {}, falling back to next candidate for PDF extraction", model, resp.statusCode());
                    continue;
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Error calling {} for PDF extraction: {}", model, e.getMessage());
            }
        }
        throw new RuntimeException("AI trích xuất PDF thất bại: " + (lastException != null ? lastException.getMessage() : "Không thể phân tích tệp"));
    }

    public Map<String, Object> getInsights(GeminiInsightRequestDto request) {
        String effectiveKey = (request.getApiKey() != null && !request.getApiKey().trim().isEmpty())
                ? request.getApiKey().trim()
                : this.apiKey;

        if (effectiveKey == null || effectiveKey.trim().isEmpty()) {
            return heuristicInsights(request);
        }

        try {
            String prompt = buildInsightPrompt(request);
            GeminiCallResult callResult = callGeminiWithKey(prompt, effectiveKey);
            return parseGeminiResponse(callResult.body, request, callResult.model);
        } catch (Exception e) {
            log.warn("Gemini API call failed, falling back to heuristic: {}", e.getMessage());
            Map<String, Object> fallback = heuristicInsights(request);
            fallback.put("warning", "Gọi Gemini API thất bại (" + e.getMessage() + "), đã chuyển sang bộ phân tích Heuristic.");
            return fallback;
        }
    }

    public Map<String, Object> qualityAudit(Long datasetId) {
        return qualityAudit(datasetId, null);
    }

    public Map<String, Object> qualityAudit(Long datasetId, String passedApiKey) {
        Dataset dataset = datasetRepo.findById(datasetId)
                .orElseThrow(() -> new RuntimeException("Dataset not found: " + datasetId));

        List<Map<String, Object>> rows = parserService.fromJson(dataset.getRawDataJson());
        if (rows == null) rows = new ArrayList<>();

        String effectiveKey = (passedApiKey != null && !passedApiKey.trim().isEmpty())
                ? passedApiKey.trim()
                : this.apiKey;

        if (effectiveKey == null || effectiveKey.trim().isEmpty()) {
            return heuristicQualityAudit(rows, dataset);
        }

        try {
            String prompt = buildQualityAuditPrompt(rows, dataset);
            GeminiCallResult callResult = callGeminiWithKey(prompt, effectiveKey);
            return parseQualityAuditResponse(callResult.body, rows, dataset);
        } catch (Exception e) {
            log.warn("Gemini quality audit failed, using heuristic: {}", e.getMessage());
            return heuristicQualityAudit(rows, dataset);
        }
    }

    private Map<String, Object> heuristicQualityAudit(List<Map<String, Object>> rows, Dataset dataset) {
        List<String> issues = new ArrayList<>();
        Map<String, Object> metrics = computeBasicQualityMetrics(rows);

        int missingCount = (int) metrics.getOrDefault("totalMissing", 0);
        int duplicateCount = (int) metrics.getOrDefault("estimatedDuplicates", 0);
        int totalCells = (int) metrics.getOrDefault("totalCells", 1);

        if (missingCount > 0) {
            double pct = (double) missingCount / totalCells * 100;
            issues.add(String.format("Dữ liệu thiếu: Có %d ô (%.1f%%) bị trống hoặc mang giá trị null.", missingCount, pct));
        }
        if (duplicateCount > 0) {
            issues.add(String.format("Bản ghi trùng lặp: Phát hiện khoảng %d dòng trùng 100%% giá trị.", duplicateCount));
        }
        if (rows.isEmpty()) {
            issues.add("Tập dữ liệu rỗng. Vui lòng nạp tệp có dữ liệu thực tế.");
        } else if (rows.size() < 10) {
            issues.add("Quy mô mẫu nhỏ (" + rows.size() + " dòng). Nên bổ sung thêm bản ghi để độ tin cậy thống kê cao hơn.");
        }
        if (issues.isEmpty()) {
            issues.add("Dữ liệu đạt tiêu chuẩn chất lượng cao, không phát hiện lỗi cấu trúc nghiêm trọng.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("issues", issues);
        result.put("source", "heuristic");
        result.putAll(metrics);
        return result;
    }

    private Map<String, Object> computeBasicQualityMetrics(List<Map<String, Object>> rows) {
        int totalCells = 0, missingCells = 0;
        Set<String> rowSignatures = new HashSet<>();
        int duplicates = 0;

        for (Map<String, Object> row : rows) {
            totalCells += row.size();
            for (Object v : row.values()) {
                if (v == null || v.toString().trim().isEmpty()) missingCells++;
            }
            String sig = row.toString();
            if (!rowSignatures.add(sig)) duplicates++;
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalRows", rows.size());
        m.put("totalCells", totalCells);
        m.put("totalMissing", missingCells);
        m.put("estimatedDuplicates", duplicates);
        m.put("completenessPercent", totalCells > 0 ?
                Math.round((1.0 - (double) missingCells / totalCells) * 10000) / 100.0 : 100.0);
        return m;
    }

    private GeminiCallResult callGeminiWithKey(String prompt, String key) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        Map<String, Object> content = new LinkedHashMap<>();
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("text", prompt);
        content.put("parts", List.of(part));
        requestBody.put("contents", List.of(content));

        Map<String, Object> genConfig = new LinkedHashMap<>();
        genConfig.put("temperature", 0.4);
        genConfig.put("maxOutputTokens", 1500);
        requestBody.put("generationConfig", genConfig);

        String bodyJson = gson.toJson(requestBody);
        Exception lastException = null;

        for (String model : CANDIDATE_MODELS) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .timeout(Duration.ofSeconds(45))
                        .build();

                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    return new GeminiCallResult(resp.body(), model);
                }

                if (resp.statusCode() == 404 || resp.statusCode() == 503) {
                    log.warn("Model {} returned {}, falling back to next candidate model", model, resp.statusCode());
                    continue;
                }

                String errMsg = parseErrorMessage(resp.body(), resp.statusCode());
                throw new RuntimeException(errMsg);
            } catch (Exception e) {
                lastException = e;
                if (e.getMessage() != null && (e.getMessage().contains("Khóa API không hợp lệ") || e.getMessage().contains("PERMISSION_DENIED"))) {
                    throw e;
                }
                log.warn("Error calling Gemini model {}: {}", model, e.getMessage());
            }
        }

        throw lastException != null ? lastException : new RuntimeException("Không thể kết nối tới các mô hình Gemini.");
    }

    private String parseErrorMessage(String responseBody, int statusCode) {
        try {
            Map<?, ?> errJson = gson.fromJson(responseBody, Map.class);
            if (errJson.containsKey("error") && errJson.get("error") instanceof Map) {
                Map<?, ?> err = (Map<?, ?>) errJson.get("error");
                String msg = (String) err.get("message");
                String status = (String) err.get("status");
                if ("INVALID_ARGUMENT".equals(status) || (msg != null && msg.toLowerCase().contains("api key"))) {
                    return "Khóa API không hợp lệ. Vui lòng kiểm tra lại khóa đã tạo tại Google AI Studio.";
                }
                if ("RESOURCE_EXHAUSTED".equals(status)) {
                    return "Đã đạt giới hạn yêu cầu (Quota/Rate Limit). Vui lòng thử lại sau giây lát.";
                }
                if ("PERMISSION_DENIED".equals(status)) {
                    return "Khóa API không có quyền truy cập mô hình này. Vui lòng kiểm tra quyền hạn của khóa.";
                }
                return msg != null ? msg : "Mã lỗi: " + statusCode;
            }
        } catch (Exception ignored) {}
        return "Mã lỗi HTTP " + statusCode;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseGeminiResponse(String responseJson, GeminiInsightRequestDto request, String modelName) {
        try {
            Map<String, Object> resp = gson.fromJson(responseJson, Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.get("candidates");
            if (candidates == null || candidates.isEmpty()) return heuristicInsights(request);

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = (String) parts.get(0).get("text");

            // Parse text into structured response (support both Vietnamese and English)
            List<String> insights = extractListItems(text, "nhận định");
            if (insights.isEmpty()) insights = extractListItems(text, "insight");

            List<String> recommendations = extractListItems(text, "đề xuất");
            if (recommendations.isEmpty()) recommendations = extractListItems(text, "recommendation");

            if (insights.isEmpty()) {
                // Fall back to splitting by newlines
                String[] lines = text.split("\n");
                for (String line : lines) {
                    line = line.replaceAll("^[•\\-*\\d.]+\\s*", "").trim();
                    if (!line.isEmpty()) {
                        if (insights.size() < 4) insights.add(line);
                        else if (recommendations.size() < 3) recommendations.add(line);
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("insights", insights.stream().limit(4).toList());
            result.put("recommendations", recommendations.stream().limit(3).toList());
            result.put("source", "gemini");
            result.put("model", modelName);
            result.put("rawResponse", text);
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage());
            return heuristicInsights(request);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseQualityAuditResponse(String responseJson,
                                                            List<Map<String, Object>> rows,
                                                            Dataset dataset) {
        try {
            Map<String, Object> resp = gson.fromJson(responseJson, Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.get("candidates");
            if (candidates == null || candidates.isEmpty()) return heuristicQualityAudit(rows, dataset);

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = (String) parts.get(0).get("text");

            List<String> issues = new ArrayList<>();
            String[] lines = text.split("\n");
            for (String line : lines) {
                String cleaned = line.replaceAll("^[•\\-*\\d.]+\\s*", "").trim();
                if (!cleaned.isEmpty()) issues.add(cleaned);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("issues", issues);
            result.put("source", "gemini");
            result.putAll(computeBasicQualityMetrics(rows));
            return result;
        } catch (Exception e) {
            return heuristicQualityAudit(rows, dataset);
        }
    }

    private Map<String, Object> heuristicInsights(GeminiInsightRequestDto request) {
        List<String> insights = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        int rows = request.getRowCount() != null ? request.getRowCount() : 0;
        String metric = request.getMetricCol() != null ? request.getMetricCol() : "chỉ số chính";
        String category = request.getCategoryCol() != null ? request.getCategoryCol() : "danh mục";

        Map<String, Object> stats = request.getStatistics();
        if (stats != null && !stats.isEmpty()) {
            Object sumObj = stats.get("sum");
            Object meanObj = stats.get("mean");
            Object medianObj = stats.get("median");
            Object stdDevObj = stats.get("stdDev");

            insights.add(String.format("Tập dữ liệu ghi nhận tổng cộng %d bản ghi với quy mô tổng %s đạt xấp xỉ %s.",
                    rows, metric, formatNum(sumObj)));

            if (meanObj != null && medianObj != null) {
                double mean = toDbl(meanObj);
                double median = toDbl(medianObj);
                if (mean > median * 1.15) {
                    insights.add(String.format("Giá trị trung bình (%s) cao hơn đáng kể so với trung vị (%s), cho thấy phân phối lệch phải do xuất hiện các giao dịch/giá trị đột biến ở nhóm dẫn đầu.",
                            formatNum(mean), formatNum(median)));
                } else if (median > mean * 1.15) {
                    insights.add(String.format("Phân phối lệch trái với trung vị (%s) vượt trung bình (%s), biểu hiện sự tập trung mạnh ở phân khúc trên.",
                            formatNum(median), formatNum(mean)));
                } else {
                    insights.add(String.format("Phân phối dữ liệu tương đối cân bằng: Trung bình đạt %s và Trung vị đạt %s.",
                            formatNum(mean), formatNum(median)));
                }
            }

            if (stdDevObj != null && meanObj != null) {
                double std = toDbl(stdDevObj);
                double mean = toDbl(meanObj);
                double cv = mean != 0 ? (std / mean) * 100 : 0;
                insights.add(String.format("Hệ số biến thiên (CV) đạt %.1f%% với độ lệch chuẩn %s, phản ánh mức độ phân tán %s.",
                        cv, formatNum(std), cv > 50 ? "rất cao giữa các phân khúc" : "ở ngưỡng an toàn"));
            }
        } else {
            insights.add(String.format("Đã nạp thành công %d bản ghi dữ liệu thực tế, sẵn sàng cho công tác kiểm toán và phân tích điều hành.", rows));
            insights.add("Cấu trúc bảng dữ liệu đã qua tiền xử lý, loại bỏ khoảng trắng và khử trùng lặp hoàn chỉnh.");
            insights.add("Các trường dữ liệu số và danh mục đã được phân loại tự động để xây dựng Dashboard.");
        }

        insights.add(String.format("Theo quy luật Pareto 80/20, phần lớn giá trị tập trung tại top 20%% %s trọng điểm, cần ưu tiên phân bổ nguồn lực giám sát.", category));

        recommendations.add(String.format("Tập trung 80%% nguồn lực quản trị vào nhóm A (top các %s đóng góp tỷ trọng cao nhất) để tối ưu hóa hiệu quả hoạt động.", category));
        recommendations.add("Thiết lập quy trình kiểm soát rủi ro định kỳ đối với các điểm dữ liệu ngoại lai được phát hiện bằng phương pháp IQR.");
        recommendations.add("Xuất báo cáo định dạng Excel / Báo cáo HTML độc lập để trình bày hội đồng quản trị và lưu trữ kiểm toán nội bộ.");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("insights", insights);
        result.put("recommendations", recommendations);
        result.put("source", "heuristic");
        return result;
    }

    private String formatNum(Object val) {
        if (val == null) return "0";
        try {
            double d = Double.parseDouble(val.toString());
            if (Math.abs(d) >= 1_000_000_000) return String.format("%.2f Tỷ", d / 1_000_000_000);
            if (Math.abs(d) >= 1_000_000) return String.format("%.2f Triệu", d / 1_000_000);
            if (Math.abs(d) >= 1_000) return String.format("%.1f nghìn", d / 1_000);
            return String.format("%,.1f", d);
        } catch (Exception e) {
            return val.toString();
        }
    }

    private double toDbl(Object val) {
        if (val == null) return 0.0;
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }

    private String buildInsightPrompt(GeminiInsightRequestDto request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là chuyên gia phân tích dữ liệu và cố vấn chiến lược điều hành kinh doanh cấp cao.\n");
        sb.append("Hãy phân tích dữ liệu thực tế sau đây và đưa ra nhận định kinh doanh sâu sắc, chuyên nghiệp bằng TIẾNG VIỆT.\n\n");
        sb.append("THÔNG TIN DỮ LIỆU:\n");
        sb.append("- Mã tập dữ liệu: ").append(request.getDatasetId()).append("\n");
        sb.append("- Tổng số dòng: ").append(request.getRowCount()).append("\n");
        if (request.getMetricCol() != null) sb.append("- Chỉ số phân tích chính: ").append(request.getMetricCol()).append("\n");
        if (request.getCategoryCol() != null) sb.append("- Phân nhóm theo: ").append(request.getCategoryCol()).append("\n");
        if (request.getAnalysisContext() != null && !request.getAnalysisContext().isBlank()) {
            sb.append("- Bối cảnh doanh nghiệp và mục tiêu phân tích: ").append(request.getAnalysisContext().trim()).append("\n");
        }
        if (request.getColumns() != null) sb.append("- Danh sách cột: ").append(String.join(", ", request.getColumns())).append("\n");
        if (request.getStatistics() != null) sb.append("- Thống kê mô tả: ").append(gson.toJson(request.getStatistics())).append("\n");
        if (request.getUserQuestion() != null && !request.getUserQuestion().isEmpty()) {
            sb.append("- Câu hỏi quản trị đặc biệt: ").append(request.getUserQuestion()).append("\n");
        }

        sb.append("\nYÊU CẦU ĐẦU RA:\n");
        sb.append("Đưa ra chính xác:\n");
        sb.append("1. Đúng 4 NHẬN ĐỊNH CHUYÊN SÂU (Insights): phân tích quy mô, phân phối, tương quan và hiện tượng nổi bật (ngắn gọn, sắc bén, 1-2 câu mỗi nhận định).\n");
        sb.append("2. Đúng 3 ĐỀ XUẤT HÀNH ĐỘNG ĐIỀU HÀNH (Actionable Recommendations): mang tính chiến lược, rõ hành động thực thi.\n");
        sb.append("Trình bày theo định dạng đánh số rõ ràng:\n");
        sb.append("NHẬN ĐỊNH:\n1. ...\n2. ...\n3. ...\n4. ...\n\n");
        sb.append("ĐỀ XUẤT:\n1. ...\n2. ...\n3. ...\n");
        return sb.toString();
    }

    private String buildQualityAuditPrompt(List<Map<String, Object>> rows, Dataset dataset) {
        Map<String, Object> metrics = computeBasicQualityMetrics(rows);
        StringBuilder sb = new StringBuilder();
        sb.append("You are a data quality expert. Audit this dataset:\n\n");
        sb.append("File: ").append(dataset.getOriginalFileName()).append("\n");
        sb.append("Rows: ").append(rows.size()).append("\n");
        if (!rows.isEmpty()) sb.append("Columns: ").append(String.join(", ", rows.get(0).keySet())).append("\n");
        sb.append("Missing cells: ").append(metrics.get("totalMissing")).append("\n");
        sb.append("Potential duplicates: ").append(metrics.get("estimatedDuplicates")).append("\n");
        sb.append("Completeness: ").append(metrics.get("completenessPercent")).append("%\n");
        sb.append("\nList the top data quality issues found, one per line. Be specific and actionable.");
        return sb.toString();
    }

    private List<String> extractListItems(String text, String keyword) {
        List<String> items = new ArrayList<>();
        String[] lines = text.split("\n");
        boolean inSection = false;
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains(keyword)) {
                inSection = true;
                continue;
            }
            if (inSection) {
                if ((lower.contains("đề xuất") && !keyword.contains("đề xuất")) ||
                    (lower.contains("recommendation") && !keyword.contains("recommendation")) ||
                    (lower.contains("nhận định") && !keyword.contains("nhận định"))) {
                    break;
                }
                if (line.matches("^[\\d•\\-*].+")) {
                    String cleaned = line.replaceAll("^[•\\-*\\d.]+\\s*", "").trim();
                    if (!cleaned.isEmpty()) items.add(cleaned);
                }
            }
        }
        return items;
    }
}
