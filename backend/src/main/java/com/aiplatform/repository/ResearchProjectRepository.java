package com.aiplatform.repository;

import com.aiplatform.model.ResearchProject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResearchProjectRepository extends JpaRepository<ResearchProject, Long> {
    List<ResearchProject> findAllByOrderByIdDesc();
}
