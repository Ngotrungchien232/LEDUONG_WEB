package com.datalens.repository;

import com.datalens.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    void deleteByDatasetId(Long datasetId);
    List<AuditLog> findByDatasetIdOrderByCreatedAtDesc(Long datasetId);
    List<AuditLog> findBySessionIdOrderByRowNumberAsc(Long sessionId);
}
