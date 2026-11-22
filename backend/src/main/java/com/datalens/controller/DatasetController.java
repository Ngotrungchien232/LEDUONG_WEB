package com.datalens.controller;

import com.datalens.dto.UploadResponseDto;
import com.datalens.model.Dataset;
import com.datalens.repository.DatasetRepository;
import com.datalens.repository.AnalysisReportRepository;
import com.datalens.repository.AuditLogRepository;
import com.datalens.repository.CleaningSessionRepository;
import com.datalens.service.FileParserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/dataset")
public class DatasetController {

    private final DatasetRepository datasetRepo;
    private final FileParserService parserService;
    private final AuditLogRepository auditRepo;
    private final CleaningSessionRepository sessionRepo;
    private final AnalysisReportRepository reportRepo;

    public DatasetController(DatasetRepository datasetRepo, FileParserService parserService,
                             AuditLogRepository auditRepo, CleaningSessionRepository sessionRepo,
                             AnalysisReportRepository reportRepo) {
        this.datasetRepo = datasetRepo;
        this.parserService = parserService;
        this.auditRepo = auditRepo;
        this.sessionRepo = sessionRepo;
        this.reportRepo = reportRepo;
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadResponseDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sheetName", required = false) String sheetName,
            @RequestParam(value = "businessContext", required = false) String businessContext,
            @RequestParam(value = "dataGoal", required = false) String dataGoal,
            @RequestHeader(value = "X-Gemini-Api-Key", required = false) String apiKeyHeader) {
        try {
            if (file.isEmpty()) throw new IllegalArgumentException("Tệp tải lên rỗng.");
            if (businessContext != null && businessContext.length() > 500) throw new IllegalArgumentException("Bối cảnh doanh nghiệp tối đa 500 ký tự.");
            if (dataGoal != null && dataGoal.length() > 1000) throw new IllegalArgumentException("Bài toán cần giải quyết tối đa 1.000 ký tự.");
            FileParserService.ParseResult result = parserService.parse(file, apiKeyHeader, sheetName);

            Dataset dataset = new Dataset(file.getOriginalFilename(), LocalDateTime.now());
            dataset.setFileType(result.fileType);
            dataset.setBusinessContext(trimToNull(businessContext));
            dataset.setDataGoal(trimToNull(dataGoal));
            dataset.setSheetName(result.sheetName != null ? result.sheetName : "Sheet1");
            dataset.setRowCount(result.rows.size());
            dataset.setColumnCount(result.columns.size());
            dataset.setRawDataJson(parserService.toJson(result.rows));
            dataset.setSourceDataJson(dataset.getRawDataJson());
            dataset.setColumnNamesJson(parserService.toJson(result.columns));
            dataset.setStatus("UPLOADED");
            dataset = datasetRepo.save(dataset);

            UploadResponseDto resp = new UploadResponseDto();
            resp.setDatasetId(dataset.getId());
            resp.setOriginalFileName(dataset.getOriginalFileName());
            resp.setFileType(dataset.getFileType());
            resp.setSheetName(dataset.getSheetName());
            resp.setBusinessContext(dataset.getBusinessContext());
            resp.setDataGoal(dataset.getDataGoal());
            resp.setRowCount(dataset.getRowCount());
            resp.setColumnCount(dataset.getColumnCount());
            resp.setColumns(result.columns);
            resp.setUploadedAt(dataset.getUploadedAt());
            resp.setStatus(dataset.getStatus());
            resp.setMessage("Upload and parsing successful");

            int previewLimit = Math.min(20, result.rows.size());
            resp.setPreviewRows(new ArrayList<>(result.rows.subList(0, previewLimit)));

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            UploadResponseDto err = new UploadResponseDto();
            err.setStatus("ERROR");
            err.setMessage(e.getMessage() != null ? e.getMessage() : "Error parsing file");
            return ResponseEntity.badRequest().body(err);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<Dataset>> list() {
        List<Dataset> list = datasetRepo.findAllByOrderByUploadedAtDesc();
        // Clear rawDataJson to keep summary payload fast and lean
        for (Dataset d : list) {
            d.setRawDataJson(null);
            d.setSourceDataJson(null);
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id,
                                     @RequestParam(defaultValue = "false") boolean includeRows) {
        return datasetRepo.findById(id)
                .map(d -> {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("id", d.getId());
                    resp.put("originalFileName", d.getOriginalFileName());
                    resp.put("fileType", d.getFileType());
                    resp.put("sheetName", d.getSheetName());
                    resp.put("businessContext", d.getBusinessContext());
                    resp.put("dataGoal", d.getDataGoal());
                    resp.put("rowCount", d.getRowCount());
                    resp.put("columnCount", d.getColumnCount());
                    resp.put("status", d.getStatus());
                    resp.put("canUndo", d.getSourceDataJson() != null && !"UPLOADED".equalsIgnoreCase(d.getStatus()));
                    resp.put("uploadedAt", d.getUploadedAt());
                    List<String> cols = parserService.fromJsonStringList(d.getColumnNamesJson());
                    resp.put("columns", cols != null ? cols : Collections.emptyList());

                    List<Map<String, Object>> rows = parserService.fromJson(d.getRawDataJson());
                    if (rows == null) rows = Collections.emptyList();
                    if (includeRows) resp.put("rows", rows);
                    int previewLimit = Math.min(20, rows.size());
                    resp.put("previewRows", rows.subList(0, previewLimit));
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/page")
    public ResponseEntity<?> getPage(@PathVariable Long id,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size,
                                     @RequestParam(required = false) String search,
                                     @RequestParam(required = false) String sort,
                                     @RequestParam(defaultValue = "asc") String direction,
                                     @RequestParam(defaultValue = "all") String filterMode,
                                     @RequestParam Map<String, String> params) {
        if (page < 0 || size < 1 || size > 200) return ResponseEntity.badRequest().body(Map.of("message", "Trang phải từ 0 trở lên và kích thước trang từ 1 đến 200."));
        return datasetRepo.findById(id).map(dataset -> {
            List<Map<String, Object>> parsedRows = parserService.fromJson(dataset.getRawDataJson());
            if (parsedRows == null) parsedRows = new ArrayList<>();
            List<String> parsedColumns = parserService.fromJsonStringList(dataset.getColumnNamesJson());
            final List<String> columns = parsedColumns == null ? Collections.emptyList() : parsedColumns;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < parsedRows.size(); i++) {
                Map<String, Object> row = new LinkedHashMap<>(parsedRows.get(i));
                row.put("__sourceRowNumber", i + 1);
                rows.add(row);
            }
            int allRows = rows.size();
            long outlierCount = rows.stream().filter(row -> row.keySet().stream()
                    .anyMatch(key -> key.startsWith("__outlier_") && Boolean.TRUE.equals(row.get(key)))).count();
            Set<Integer> modifiedRows = sessionRepo.findTopByDatasetIdOrderByStartedAtDesc(id)
                    .map(session -> auditRepo.findBySessionIdOrderByRowNumberAsc(session.getId()))
                    .orElseGet(Collections::emptyList).stream()
                    .filter(log -> log.getColumnName() != null && !"*".equals(log.getColumnName()))
                    .filter(log -> log.getChangeReason() == null || !log.getChangeReason().contains("OUTLIER"))
                    .map(com.datalens.model.AuditLog::getRowNumber).collect(java.util.stream.Collectors.toSet());
            java.util.stream.Stream<Map<String, Object>> stream = rows.stream();
            if (search != null && !search.isBlank()) {
                String term = search.toLowerCase(Locale.ROOT);
                stream = stream.filter(row -> columns.stream().anyMatch(col -> Objects.toString(row.get(col), "").toLowerCase(Locale.ROOT).contains(term)));
            }
            Map<String, String> filters = new LinkedHashMap<>();
            params.forEach((key, value) -> {
                if (key.startsWith("filter.")) filters.put(key.substring(7), value);
            });
            for (Map.Entry<String, String> filter : filters.entrySet()) {
                String column = filter.getKey();
                if (!columns.contains(column) || filter.getValue() == null || filter.getValue().isBlank()) continue;
                String term = filter.getValue().toLowerCase(Locale.ROOT);
                stream = stream.filter(row -> Objects.toString(row.get(column), "").toLowerCase(Locale.ROOT).contains(term));
            }
            if ("outlier".equals(filterMode)) {
                stream = stream.filter(row -> row.keySet().stream().anyMatch(key -> key.startsWith("__outlier_") && Boolean.TRUE.equals(row.get(key))));
            } else if ("modified".equals(filterMode)) {
                stream = stream.filter(row -> modifiedRows.contains(((Number) row.get("__sourceRowNumber")).intValue()));
            }
            List<Map<String, Object>> filtered = stream.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (sort != null && columns.contains(sort)) {
                Comparator<Map<String, Object>> comparator = (left, right) -> {
                    Object leftValue = left.get(sort);
                    Object rightValue = right.get(sort);
                    if (leftValue instanceof Number a && rightValue instanceof Number b) return Double.compare(a.doubleValue(), b.doubleValue());
                    return Objects.toString(leftValue, "").compareToIgnoreCase(Objects.toString(rightValue, ""));
                };
                if ("desc".equalsIgnoreCase(direction)) comparator = comparator.reversed();
                filtered.sort(comparator);
            }
            int total = filtered.size();
            int start = (int) Math.min(total, (long) page * size);
            int end = (int) Math.min(total, (long) start + size);
            List<Map<String, Object>> pageRows = new ArrayList<>(filtered.subList(start, end));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("datasetId", id);
            response.put("rows", pageRows);
            response.put("columns", columns);
            response.put("totalRows", total);
            response.put("allRows", allRows);
            response.put("modifiedRows", modifiedRows.size());
            response.put("outlierRows", outlierCount);
            response.put("page", page);
            response.put("size", size);
            response.put("totalPages", Math.max(1, (int) Math.ceil((double) total / size)));
            return ResponseEntity.ok(response);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/cell")
    @Transactional
    public ResponseEntity<?> updateCell(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        Dataset dataset = datasetRepo.findById(id).orElse(null);
        if (dataset == null) return ResponseEntity.notFound().build();
        Object rowValue = request.get("rowNumber");
        String column = Objects.toString(request.get("columnName"), "");
        if (!(rowValue instanceof Number)) return ResponseEntity.badRequest().body(Map.of("message", "Số dòng không hợp lệ."));
        int rowNumber = ((Number) rowValue).intValue();
        List<String> columns = parserService.fromJsonStringList(dataset.getColumnNamesJson());
        if (columns == null || !columns.contains(column)) return ResponseEntity.badRequest().body(Map.of("message", "Cột dữ liệu không hợp lệ."));

        List<Map<String, Object>> rows = parserService.fromJson(dataset.getRawDataJson());
        if (rows == null || rowNumber < 1 || rowNumber > rows.size()) return ResponseEntity.badRequest().body(Map.of("message", "Dòng dữ liệu không tồn tại."));
        Map<String, Object> row = rows.get(rowNumber - 1);
        Object before = row.get(column);
        Object submitted = request.get("value");
        Object after;
        try {
            after = coerceCellValue(submitted, before);
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
        if (Objects.equals(before, after)) return ResponseEntity.ok(Map.of("updated", false, "message", "Giá trị không thay đổi."));

        row.put(column, after);
        dataset.setRawDataJson(parserService.toJson(rows));
        dataset.setRowCount(rows.size());
        dataset.setStatus("CLEANED");
        datasetRepo.save(dataset);

        var session = sessionRepo.findTopByDatasetIdOrderByStartedAtDesc(id).orElseGet(() -> new com.datalens.model.CleaningSession(id));
        session.setStartedAt(LocalDateTime.now());
        session.setCompletedAt(LocalDateTime.now());
        session.setStatus("COMPLETED");
        session.setRowsAffected((session.getRowsAffected() == null ? 0 : session.getRowsAffected()) + 1);
        session.setCleanedDataJson(parserService.toJson(rows));
        if (session.getRulesAppliedJson() == null) session.setRulesAppliedJson("[\"MANUAL_EDIT\"]");
        session = sessionRepo.save(session);
        auditRepo.save(new com.datalens.model.AuditLog(id, session.getId(), rowNumber, column,
                Objects.toString(before, ""), Objects.toString(after, ""), "MANUAL_EDIT"));
        reportRepo.deleteByDatasetId(id);

        return ResponseEntity.ok(Map.of("updated", true, "rowNumber", rowNumber,
                "columnName", column, "value", after == null ? "" : after, "status", dataset.getStatus()));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (datasetRepo.existsById(id)) {
            auditRepo.deleteByDatasetId(id);
            sessionRepo.deleteByDatasetId(id);
            reportRepo.deleteByDatasetId(id);
            datasetRepo.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private Object coerceCellValue(Object submitted, Object before) {
        if (submitted == null) return null;
        String text = submitted.toString();
        if (text.length() > 5000) throw new IllegalArgumentException("Giá trị ô tối đa 5.000 ký tự.");
        if (text.isBlank()) return null;
        if (before instanceof Number) {
            try {
                double parsed = Double.parseDouble(text);
                if (!Double.isFinite(parsed)) throw new NumberFormatException();
                if (before instanceof Integer || before instanceof Long) {
                    if (parsed != Math.rint(parsed)) throw new NumberFormatException();
                    return (long) parsed;
                }
                return parsed;
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Ô này cần một số hợp lệ. Hãy dùng dấu chấm cho phần thập phân.");
            }
        }
        if (before instanceof Boolean) {
            if ("true".equalsIgnoreCase(text)) return true;
            if ("false".equalsIgnoreCase(text)) return false;
            throw new IllegalArgumentException("Ô này chỉ nhận giá trị true hoặc false.");
        }
        return text;
    }
}
