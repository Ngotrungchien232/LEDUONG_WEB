package com.datalens.service;

import com.datalens.model.Dataset;
import com.datalens.repository.AnalysisReportRepository;
import com.datalens.repository.AuditLogRepository;
import com.datalens.repository.CleaningSessionRepository;
import com.datalens.repository.DatasetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DatasetRetentionService {
    private static final Logger log = LoggerFactory.getLogger(DatasetRetentionService.class);

    private final DatasetRepository datasetRepo;
    private final AuditLogRepository auditRepo;
    private final CleaningSessionRepository sessionRepo;
    private final AnalysisReportRepository reportRepo;

    @Value("${datalens.retention.days:0}")
    private int retentionDays;

    public DatasetRetentionService(DatasetRepository datasetRepo, AuditLogRepository auditRepo,
                                   CleaningSessionRepository sessionRepo, AnalysisReportRepository reportRepo) {
        this.datasetRepo = datasetRepo;
        this.auditRepo = auditRepo;
        this.sessionRepo = sessionRepo;
        this.reportRepo = reportRepo;
    }

    @Scheduled(cron = "${datalens.retention.cron:0 0 3 * * *}")
    @Transactional
    public void deleteExpiredDatasets() {
        if (retentionDays <= 0) return;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        for (Dataset dataset : datasetRepo.findByUploadedAtBefore(cutoff)) {
            Long id = dataset.getId();
            auditRepo.deleteByDatasetId(id);
            sessionRepo.deleteByDatasetId(id);
            reportRepo.deleteByDatasetId(id);
            datasetRepo.delete(dataset);
        }
        log.info("Removed datasets older than {} days", retentionDays);
    }
}
