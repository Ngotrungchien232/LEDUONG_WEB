package com.datalens.controller;

import com.datalens.dto.CleanRequestDto;
import com.datalens.dto.CleanResponseDto;
import com.datalens.model.AuditLog;
import com.datalens.model.Dataset;
import com.datalens.repository.AuditLogRepository;
import com.datalens.repository.DatasetRepository;
import com.datalens.repository.CleaningSessionRepository;
import com.datalens.service.DataCleanerService;
import com.datalens.service.FileParserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@RestController
@RequestMapping("/api/clean")
public class CleaningController {

    private final DataCleanerService cleanerService;
    private final AuditLogRepository auditRepo;
    private final DatasetRepository datasetRepo;
    private final FileParserService parserService;
    private final CleaningSessionRepository sessionRepo;

    public CleaningController(DataCleanerService cleanerService,
                              AuditLogRepository auditRepo,
                              DatasetRepository datasetRepo,
                              FileParserService parserService,
                              CleaningSessionRepository sessionRepo) {
        this.cleanerService = cleanerService;
        this.auditRepo = auditRepo;
        this.datasetRepo = datasetRepo;
        this.parserService = parserService;
        this.sessionRepo = sessionRepo;
    }

    @PostMapping("/{datasetId}")
    public ResponseEntity<CleanResponseDto> clean(
            @PathVariable Long datasetId,
            @RequestBody CleanRequestDto req) {
        CleanResponseDto resp = cleanerService.clean(datasetId, req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{datasetId}/undo")
    @Transactional
    public ResponseEntity<?> undo(@PathVariable Long datasetId) {
        return datasetRepo.findById(datasetId).map(dataset -> {
            String source = dataset.getSourceDataJson() != null
                    ? dataset.getSourceDataJson() : dataset.getRawDataJson();
            List<Map<String, Object>> sourceRows = parserService.fromJson(source);
            if (sourceRows == null) sourceRows = Collections.emptyList();
            dataset.setRawDataJson(source);
            dataset.setRowCount(sourceRows.size());
            dataset.setStatus("UPLOADED");
            datasetRepo.save(dataset);
            return ResponseEntity.ok(Map.of("datasetId", datasetId, "status", "UPLOADED",
                    "rowCount", dataset.getRowCount(), "message", "Đã khôi phục dữ liệu gốc."));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{datasetId}/log")
    public ResponseEntity<List<AuditLog>> getLog(@PathVariable Long datasetId) {
        List<AuditLog> logs = sessionRepo.findTopByDatasetIdOrderByStartedAtDesc(datasetId)
                .map(session -> auditRepo.findBySessionIdOrderByRowNumberAsc(session.getId()))
                .orElseGet(Collections::emptyList);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/{datasetId}/preview")
    public ResponseEntity<?> getPreview(@PathVariable Long datasetId) {
        return datasetRepo.findById(datasetId)
                .map(d -> {
                    List<Map<String, Object>> rows = parserService.fromJson(d.getRawDataJson());
                    if (rows == null) rows = Collections.emptyList();
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("datasetId", d.getId());
                    resp.put("totalRows", rows.size());
                    resp.put("columns", parserService.fromJsonStringList(d.getColumnNamesJson()));
                    resp.put("rows", rows);
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
