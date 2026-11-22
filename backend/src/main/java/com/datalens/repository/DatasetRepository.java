package com.datalens.repository;

import com.datalens.model.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    List<Dataset> findAllByOrderByUploadedAtDesc();
    List<Dataset> findByUploadedAtBefore(LocalDateTime cutoff);
}
