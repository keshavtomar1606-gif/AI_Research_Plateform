package com.aiplatform.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A training job. Two kinds exist:
 * - ML_TRAINING: a real linear-regression run executed by TrainingService. Status and
 *   progress are written by the server as each step actually finishes.
 * - RECORD (or null for rows created by older versions): a plain note about an external
 *   job. No training is executed for these, so the UI labels them "Record only".
 */
@Entity
public class TrainingJob {
    public static final String TYPE_ML = "ML_TRAINING";
    public static final String TYPE_RECORD = "RECORD";

    public static final String QUEUED = "Queued";
    public static final String RUNNING = "Running";
    public static final String COMPLETED = "Completed";
    public static final String FAILED = "Failed";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String model;
    private String dataset;
    private String parameters;
    private String status;
    private Integer progress;

    private String jobType;
    private Long datasetId;
    private String targetColumn;
    @Column(length = 2000) private String featureColumns;
    private Double testRatio;
    private Long randomSeed;

    private Integer trainRows;
    private Integer testRows;
    private Integer droppedRows;
    private Double mae;
    private Double rmse;
    private Double r2;
    @Column(name = "train_r2") private Double trainR2;
    private Double baselineMae;
    @Column(length = 4000) private String coefficientsJson;
    @Column(length = 1000) private String errorMessage;

    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    public TrainingJob() {}

    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public boolean isMlJob() { return TYPE_ML.equals(jobType); }

    public Long getId() { return id; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getDataset() { return dataset; }
    public void setDataset(String dataset) { this.dataset = dataset; }
    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getTargetColumn() { return targetColumn; }
    public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
    public String getFeatureColumns() { return featureColumns; }
    public void setFeatureColumns(String featureColumns) { this.featureColumns = featureColumns; }
    public Double getTestRatio() { return testRatio; }
    public void setTestRatio(Double testRatio) { this.testRatio = testRatio; }
    public Long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(Long randomSeed) { this.randomSeed = randomSeed; }
    public Integer getTrainRows() { return trainRows; }
    public void setTrainRows(Integer trainRows) { this.trainRows = trainRows; }
    public Integer getTestRows() { return testRows; }
    public void setTestRows(Integer testRows) { this.testRows = testRows; }
    public Integer getDroppedRows() { return droppedRows; }
    public void setDroppedRows(Integer droppedRows) { this.droppedRows = droppedRows; }
    public Double getMae() { return mae; }
    public void setMae(Double mae) { this.mae = mae; }
    public Double getRmse() { return rmse; }
    public void setRmse(Double rmse) { this.rmse = rmse; }
    public Double getR2() { return r2; }
    public void setR2(Double r2) { this.r2 = r2; }
    public Double getTrainR2() { return trainR2; }
    public void setTrainR2(Double trainR2) { this.trainR2 = trainR2; }
    public Double getBaselineMae() { return baselineMae; }
    public void setBaselineMae(Double baselineMae) { this.baselineMae = baselineMae; }
    public String getCoefficientsJson() { return coefficientsJson; }
    public void setCoefficientsJson(String coefficientsJson) { this.coefficientsJson = coefficientsJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
