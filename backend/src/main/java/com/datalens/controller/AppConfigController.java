package com.datalens.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class AppConfigController {
    private final DataSize maxUploadSize;

    public AppConfigController(@Value("${spring.servlet.multipart.max-file-size:50MB}") String maxUploadSize) {
        this.maxUploadSize = DataSize.parse(maxUploadSize);
    }

    @GetMapping
    public Map<String, Object> getConfig() {
        return Map.of("maxUploadBytes", maxUploadSize.toBytes());
    }
}
