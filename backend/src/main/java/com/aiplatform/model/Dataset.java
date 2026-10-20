package com.aiplatform.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Dataset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String name;
    @Column(length = 2000) private String description;
    private String owner;
    private String fileName;
    private String status = "Ready";

    // Filled only when a CSV file is uploaded; metadata-only datasets leave these empty.
    @Lob private String csvContent;
    @Column(length = 2000) private String columnNames;
    private Integer rowCount;
    private Long sizeBytes;
    private Instant createdAt;

    public Dataset() {}

    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCsvContent() { return csvContent; }
    public void setCsvContent(String csvContent) { this.csvContent = csvContent; }
    public String getColumnNames() { return columnNames; }
    public void setColumnNames(String columnNames) { this.columnNames = columnNames; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
