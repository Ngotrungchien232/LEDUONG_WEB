package com.datalens.controller;

import com.datalens.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/{datasetId}/excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Long datasetId) throws IOException {
        byte[] bytes = exportService.exportExcel(datasetId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dataset_" + datasetId + "_clean.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/{datasetId}/csv")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long datasetId) throws IOException {
        byte[] bytes = exportService.exportCsv(datasetId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dataset_" + datasetId + "_clean.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/{datasetId}/json")
    public ResponseEntity<byte[]> exportJson(@PathVariable Long datasetId) throws IOException {
        byte[] bytes = exportService.exportJson(datasetId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dataset_" + datasetId + "_clean.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bytes);
    }

    @GetMapping("/{datasetId}/html")
    public ResponseEntity<byte[]> exportHtml(@PathVariable Long datasetId) throws IOException {
        byte[] bytes = exportService.exportHtml(datasetId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dataset_" + datasetId + "_dashboard.html\"")
                .contentType(MediaType.TEXT_HTML)
                .body(bytes);
    }
}
