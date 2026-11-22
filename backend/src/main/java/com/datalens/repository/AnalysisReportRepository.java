package com.datalens.repository;

import com.datalens.model.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {
    void deleteByDatasetId(Long datasetId);
    List<AnalysisReport> findByDatasetIdOrderByCreatedAtDesc(Long datasetId);
    Optional<AnalysisReport> findTopByDatasetIdOrderByCreatedAtDesc(Long datasetId);
}
