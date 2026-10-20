package com.aiplatform.controller;

import com.aiplatform.dto.DatasetPreview;
import com.aiplatform.dto.DatasetRequest;
import com.aiplatform.dto.DatasetSummary;
import com.aiplatform.exception.TrainingDataException;
import com.aiplatform.ml.CsvTable;
import com.aiplatform.model.Dataset;
import com.aiplatform.repository.DatasetRepository;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {
    public static final String SAMPLE_NAME = "Student Performance (sample CSV)";
    private static final String SAMPLE_FILE = "sample-data/student_performance.csv";

    private final DatasetRepository repository;
    public DatasetController(DatasetRepository repository) { this.repository = repository; }

    @GetMapping public List<DatasetSummary> all() { return repository.findAllSummaries(); }
    @GetMapping("/{id}") public DatasetSummary one(@PathVariable Long id) { return DatasetSummary.of(find(id)); }

    /** Creates a metadata-only record (no file). Use /upload to attach CSV data. */
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public DatasetSummary create(@Valid @RequestBody DatasetRequest input) {
        Dataset d = applyMetadata(new Dataset(), input);
        if (input.status() == null) d.setStatus("Metadata only");
        return DatasetSummary.of(repository.save(d));
    }

    @PutMapping("/{id}") public DatasetSummary update(@PathVariable Long id, @Valid @RequestBody DatasetRequest input) {
        Dataset d = applyMetadata(find(id), input);
        return DatasetSummary.of(repository.save(d));
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repository.delete(find(id));
    }

    /** Upload a CSV file (multipart form fields: file, name, description, owner). */
    @PostMapping(value = "/upload", consumes = "multipart/form-data") @ResponseStatus(HttpStatus.CREATED)
    public DatasetSummary upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "name", required = false) String name,
                                 @RequestParam(value = "description", required = false) String description,
                                 @RequestParam(value = "owner", required = false) String owner) throws IOException {
        String original = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
        String fileName = original.replaceAll("^.*[\\\\/]", "");
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty.");
        if (!fileName.toLowerCase().endsWith(".csv")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .csv files are supported.");
        String datasetName = name == null || name.isBlank() ? fileName.replaceAll("(?i)\\.csv$", "") : name.trim();
        if (datasetName.length() > 200 || (description != null && description.length() > 2000) || (owner != null && owner.length() > 120)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, description or owner is too long.");
        }
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        Dataset d = new Dataset();
        d.setName(datasetName);
        d.setDescription(description);
        d.setOwner(owner);
        d.setFileName(fileName);
        attachCsv(d, text);
        return DatasetSummary.of(repository.save(d));
    }

    /** Loads the bundled sample CSV. Calling it again returns the existing copy instead of duplicating it. */
    @PostMapping("/sample")
    public ResponseEntity<DatasetSummary> sample() throws IOException {
        var existing = repository.findFirstByName(SAMPLE_NAME);
        if (existing.isPresent() && existing.get().getCsvContent() != null) {
            return ResponseEntity.ok(DatasetSummary.of(existing.get()));
        }
        String text;
        try (InputStream in = new ClassPathResource(SAMPLE_FILE).getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Dataset d = new Dataset();
        d.setName(SAMPLE_NAME);
        d.setDescription("Synthetic student data (200 rows) for the linear-regression demo. Predict final_score.");
        d.setOwner("ResearchAI");
        d.setFileName("student_performance.csv");
        attachCsv(d, text);
        return ResponseEntity.status(HttpStatus.CREATED).body(DatasetSummary.of(repository.save(d)));
    }

    /** Column types, missing-value counts and the first rows, so the user can pick features and a target. */
    @GetMapping("/{id}/preview")
    public DatasetPreview preview(@PathVariable Long id) {
        Dataset d = find(id);
        if (d.getCsvContent() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This dataset has no CSV data attached.");
        }
        CsvTable table = CsvTable.parse(d.getCsvContent());
        List<String> numeric = new ArrayList<>();
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (int i = 0; i < table.columns().size(); i++) {
            if (table.isNumeric(i)) numeric.add(table.columns().get(i));
            missing.put(table.columns().get(i), table.missingCount(i));
        }
        List<List<String>> rows = table.rows().stream().limit(8).map(Arrays::asList).toList();
        return new DatasetPreview(table.columns(), numeric, missing, table.rowCount(), rows);
    }

    private static void attachCsv(Dataset d, String text) {
        CsvTable table;
        try {
            table = CsvTable.parse(text);
        } catch (TrainingDataException e) {
            throw new TrainingDataException("Invalid CSV: " + e.getMessage());
        }
        String columns = String.join("\t", table.columns());
        if (columns.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Column names are too long.");
        d.setCsvContent(text);
        d.setColumnNames(columns);
        d.setRowCount(table.rowCount());
        d.setSizeBytes((long) text.getBytes(StandardCharsets.UTF_8).length);
        d.setStatus("Ready");
    }

    private Dataset find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found."));
    }

    private static Dataset applyMetadata(Dataset d, DatasetRequest input) {
        d.setName(input.name().trim());
        d.setDescription(input.description());
        d.setOwner(input.owner());
        if (d.getCsvContent() == null) d.setFileName(input.fileName());
        if (input.status() != null) d.setStatus(input.status());
        return d;
    }
}
