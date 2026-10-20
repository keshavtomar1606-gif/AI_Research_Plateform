package com.aiplatform.model;

import jakarta.persistence.*;

@Entity
public class ResearchProject {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String title;
    @Column(length = 2000) private String description;
    private String team;
    private String status = "Active";

    public ResearchProject() {}
    public ResearchProject(String title, String description, String team, String status) {
        this.title = title; this.description = description; this.team = team; this.status = status;
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
