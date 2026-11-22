package com.datalens.controller;

import com.datalens.dto.AnalysisResponseDto;
import com.datalens.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("/{datasetId}")
    public ResponseEntity<?> analyze(
            @PathVariable Long datasetId,
            @RequestBody(required = false) Map<String, Object> body) {
        String metricCol = value(body, "metricCol");
        String categoryCol = value(body, "categoryCol");
        try {
            AnalysisResponseDto resp = analyticsService.analyze(datasetId, metricCol, categoryCol,
                    value(body, "dateCol"), value(body, "fromDate"), value(body, "toDate"), value(body, "categoryValue"));
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    private String value(Map<String, Object> body, String key) {
        Object value = body != null ? body.get(key) : null;
        return value == null ? null : value.toString();
    }
}
