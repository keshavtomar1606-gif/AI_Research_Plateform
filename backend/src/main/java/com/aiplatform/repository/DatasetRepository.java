package com.aiplatform.repository;

import com.aiplatform.model.Dataset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    /** Loads all datasets without their (possibly large) CSV content. */
    @Query("select new com.aiplatform.dto.DatasetSummary(d.id, d.name, d.description, d.owner, d.fileName, d.status,"
         + " d.columnNames, d.rowCount, d.sizeBytes, d.createdAt) from Dataset d order by d.id desc")
    List<com.aiplatform.dto.DatasetSummary> findAllSummaries();

    Optional<Dataset> findFirstByName(String name);
}
