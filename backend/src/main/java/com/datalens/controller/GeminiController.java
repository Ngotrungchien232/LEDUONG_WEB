package com.datalens.controller;

import com.datalens.dto.GeminiInsightRequestDto;
import com.datalens.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/gemini")
public class GeminiController {

    private final GeminiService geminiService;

    public GeminiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/insights")
    public ResponseEntity<Map<String, Object>> getInsights(
            @RequestHeader(value = "X-Gemini-Api-Key", required = false) String headerApiKey,
            @RequestBody GeminiInsightRequestDto req) {
        if (req.getApiKey() == null || req.getApiKey().trim().isEmpty()) {
            req.setApiKey(headerApiKey);
        }
        Map<String, Object> resp = geminiService.getInsights(req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(
            @RequestHeader(value = "X-Gemini-Api-Key", required = false) String headerApiKey,
            @RequestBody(required = false) Map<String, String> body) {
        String key = (body != null && body.containsKey("apiKey")) ? body.get("apiKey") : headerApiKey;
        Map<String, Object> resp = geminiService.testConnection(key);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/quality-audit")
    public ResponseEntity<Map<String, Object>> qualityAudit(
            @RequestHeader(value = "X-Gemini-Api-Key", required = false) String headerApiKey,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) Long datasetId) {
        Long id = datasetId;
        if (id == null && body != null && body.containsKey("datasetId")) {
            try {
                id = Long.valueOf(body.get("datasetId").toString());
            } catch (Exception ignored) {}
        }
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "datasetId is required"));
        }
        Map<String, Object> resp = geminiService.qualityAudit(id, headerApiKey);
        return ResponseEntity.ok(resp);
    }
}
