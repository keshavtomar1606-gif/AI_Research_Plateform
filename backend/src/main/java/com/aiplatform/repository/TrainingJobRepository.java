package com.aiplatform.repository;

import com.aiplatform.model.TrainingJob;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingJobRepository extends JpaRepository<TrainingJob, Long> {
    List<TrainingJob> findAllByOrderByIdDesc();
    List<TrainingJob> findByJobTypeAndStatusIn(String jobType, Collection<String> statuses);
}
