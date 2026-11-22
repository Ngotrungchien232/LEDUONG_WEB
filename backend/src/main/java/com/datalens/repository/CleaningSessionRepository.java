package com.datalens.repository;

import com.datalens.model.CleaningSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CleaningSessionRepository extends JpaRepository<CleaningSession, Long> {
    void deleteByDatasetId(Long datasetId);
    List<CleaningSession> findByDatasetIdOrderByStartedAtDesc(Long datasetId);
    Optional<CleaningSession> findTopByDatasetIdOrderByStartedAtDesc(Long datasetId);
}
