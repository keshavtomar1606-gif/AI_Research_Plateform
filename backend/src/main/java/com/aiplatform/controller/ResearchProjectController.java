package com.aiplatform.controller;

import com.aiplatform.dto.ProjectRequest;
import com.aiplatform.model.ResearchProject;
import com.aiplatform.repository.ResearchProjectRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects")
public class ResearchProjectController {
    private final ResearchProjectRepository repository;
    public ResearchProjectController(ResearchProjectRepository repository) { this.repository = repository; }

    @GetMapping public List<ResearchProject> all() { return repository.findAllByOrderByIdDesc(); }
    @GetMapping("/{id}") public ResearchProject one(@PathVariable Long id) { return find(id); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ResearchProject create(@Valid @RequestBody ProjectRequest input) {
        return repository.save(apply(new ResearchProject(), input));
    }

    @PutMapping("/{id}") public ResearchProject update(@PathVariable Long id, @Valid @RequestBody ProjectRequest input) {
        return repository.save(apply(find(id), input));
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repository.delete(find(id));
    }

    private ResearchProject find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }

    private static ResearchProject apply(ResearchProject p, ProjectRequest input) {
        p.setTitle(input.title().trim());
        p.setDescription(input.description());
        p.setTeam(input.team());
        p.setStatus(input.status() == null ? "Planning" : input.status());
        return p;
    }
}
