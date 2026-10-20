package com.aiplatform.ml;

import com.aiplatform.exception.TrainingDataException;
import com.aiplatform.dto.TrainingRunRequest;
import com.aiplatform.model.Dataset;
import com.aiplatform.model.TrainingJob;
import com.aiplatform.repository.DatasetRepository;
import com.aiplatform.repository.TrainingJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runs real linear-regression training jobs on a CSV dataset stored in MySQL.
 * Steps: load CSV -> validate columns/values -> drop rows with missing values -> shuffled train/test split
 * -> fit OLS on the training rows -> evaluate on the unseen test rows -> save metrics and coefficients.
 * The job's status and progress are saved after each step actually completes.
 */
@Service
public class TrainingService {
    private static final Logger log = LoggerFactory.getLogger(TrainingService.class);
    public static final int MIN_ROWS = 10;
    public static final double DEFAULT_TEST_RATIO = 0.2;
    public static final long DEFAULT_SEED = 42;

    private final TrainingJobRepository jobs;
    private final DatasetRepository datasets;
    private final ObjectMapper json = new ObjectMapper();
    private final ThreadPoolExecutor executor =
            new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20));

    public TrainingService(TrainingJobRepository jobs, DatasetRepository datasets) {
        this.jobs = jobs;
        this.datasets = datasets;
    }

    /** Validates the request, saves a Queued job and starts it in the background. */
    public TrainingJob start(TrainingRunRequest request) {
        Dataset dataset = datasets.findById(request.datasetId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found."));
        if (dataset.getCsvContent() == null || dataset.getCsvContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This dataset has no CSV data attached. Upload a CSV file or load the sample dataset.");
        }
        List<String> features = new ArrayList<>(new LinkedHashSet<>(request.featureColumns().stream().map(String::trim).toList()));
        String target = request.targetColumn().trim();
        if (features.contains(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The target column cannot also be a feature.");
        }
        // A broken CSV throws TrainingDataException, which the API turns into HTTP 400.
        List<String> available = CsvTable.parse(dataset.getCsvContent()).columns();
        for (String column : concat(features, target)) {
            if (!available.contains(column)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Column '" + column + "' does not exist in this dataset.");
            }
        }
        double testRatio = request.testRatio() == null ? DEFAULT_TEST_RATIO : request.testRatio();
        long seed = request.randomSeed() == null ? DEFAULT_SEED : request.randomSeed();

        TrainingJob job = new TrainingJob();
        job.setJobType(TrainingJob.TYPE_ML);
        job.setModel("Linear Regression");
        job.setDataset(dataset.getName());
        job.setDatasetId(dataset.getId());
        job.setTargetColumn(target);
        job.setFeatureColumns(String.join("\t", features));
        job.setTestRatio(testRatio);
        job.setRandomSeed(seed);
        job.setParameters("target=" + target + "; features=" + String.join(", ", features)
                + "; test split=" + Math.round(testRatio * 100) + "%; seed=" + seed);
        job.setStatus(TrainingJob.QUEUED);
        job.setProgress(0);
        TrainingJob saved = jobs.save(job);

        try {
            executor.execute(() -> run(saved.getId()));
        } catch (RejectedExecutionException e) {
            fail(saved, "Too many training jobs are waiting. Try again in a moment.");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many training jobs are waiting. Try again in a moment.");
        }
        return saved;
    }

    /** Executes one job. Public so tests can run it synchronously. */
    public void run(Long jobId) {
        TrainingJob job = jobs.findById(jobId).orElse(null);
        if (job == null) return;
        try {
            job.setStatus(TrainingJob.RUNNING);
            job.setStartedAt(Instant.now());
            job.setProgress(5);
            job = jobs.save(job);

            Dataset dataset = datasets.findById(job.getDatasetId())
                    .orElseThrow(() -> new TrainingDataException("The dataset was deleted before training started."));
            CsvTable table = CsvTable.parse(dataset.getCsvContent());
            job.setProgress(20);
            job = jobs.save(job);

            List<String> features = List.of(job.getFeatureColumns().split("\t"));
            Prepared data = prepare(table, features, job.getTargetColumn());
            job.setDroppedRows(data.dropped());
            job.setProgress(40);
            job = jobs.save(job);

            Split split = split(data, job.getTestRatio(), job.getRandomSeed(), features.size());
            job.setTrainRows(split.trainY().length);
            job.setTestRows(split.testY().length);
            job.setProgress(60);
            job = jobs.save(job);

            LinearRegression model = LinearRegression.fit(split.trainX(), split.trainY(), features);
            job.setProgress(85);
            job = jobs.save(job);

            double[] testPred = predictAll(model, split.testX());
            double[] trainPred = predictAll(model, split.trainX());
            double trainMean = mean(split.trainY());
            double[] baseline = new double[split.testY().length];
            java.util.Arrays.fill(baseline, trainMean);

            job.setMae(RegressionMetrics.mae(split.testY(), testPred));
            job.setRmse(RegressionMetrics.rmse(split.testY(), testPred));
            job.setR2(finiteOrNull(RegressionMetrics.r2(split.testY(), testPred)));
            job.setTrainR2(finiteOrNull(RegressionMetrics.r2(split.trainY(), trainPred)));
            job.setBaselineMae(RegressionMetrics.mae(split.testY(), baseline));

            Map<String, Double> coefficients = new LinkedHashMap<>();
            double[] c = model.coefficients();
            for (int i = 0; i < features.size(); i++) coefficients.put(features.get(i), c[i]);
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("intercept", model.intercept());
            stored.put("coefficients", coefficients);
            job.setCoefficientsJson(json.writeValueAsString(stored));

            job.setProgress(100);
            job.setStatus(TrainingJob.COMPLETED);
            job.setFinishedAt(Instant.now());
            jobs.save(job);
        } catch (TrainingDataException e) {
            // Problem with the data itself: the message is written for the user.
            fail(job, e.getMessage());
        } catch (Exception e) {
            log.error("Training job {} failed unexpectedly", jobId, e);
            fail(job, "Unexpected error during training. See the backend log for details.");
        }
    }

    public double predict(TrainingJob job, Map<String, Double> values) {
        if (!job.isMlJob() || !TrainingJob.COMPLETED.equals(job.getStatus()) || job.getCoefficientsJson() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only completed training runs can make predictions.");
        }
        try {
            Map<?, ?> stored = json.readValue(job.getCoefficientsJson(), Map.class);
            double result = ((Number) stored.get("intercept")).doubleValue();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) stored.get("coefficients")).entrySet()) {
                Double v = values.get((String) e.getKey());
                if (v == null || !Double.isFinite(v)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing a numeric value for '" + e.getKey() + "'.");
                }
                result += ((Number) e.getValue()).doubleValue() * v;
            }
            return result;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored model could not be read.");
        }
    }

    /** Jobs cannot survive a restart; mark them failed instead of leaving them "Running" forever. */
    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedJobs() {
        for (TrainingJob job : jobs.findByJobTypeAndStatusIn(TrainingJob.TYPE_ML, List.of(TrainingJob.QUEUED, TrainingJob.RUNNING))) {
            fail(job, "Interrupted because the backend restarted before the job finished. Run it again.");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void fail(TrainingJob job, String message) {
        job.setStatus(TrainingJob.FAILED);
        job.setErrorMessage(message == null ? "Training failed." : message.substring(0, Math.min(message.length(), 1000)));
        job.setFinishedAt(Instant.now());
        jobs.save(job);
    }

    record Prepared(double[][] x, double[] y, int dropped) {}
    record Split(double[][] trainX, double[] trainY, double[][] testX, double[] testY) {}

    static Prepared prepare(CsvTable table, List<String> features, String target) {
        int targetIdx = table.indexOf(target);
        int[] featureIdx = features.stream().mapToInt(table::indexOf).toArray();
        List<double[]> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        int dropped = 0;
        for (int r = 0; r < table.rowCount(); r++) {
            String[] row = table.rows().get(r);
            boolean missing = CsvTable.isMissing(row[targetIdx]);
            for (int idx : featureIdx) missing |= CsvTable.isMissing(row[idx]);
            if (missing) { dropped++; continue; }
            double[] x = new double[featureIdx.length];
            for (int j = 0; j < featureIdx.length; j++) x[j] = number(row[featureIdx[j]], features.get(j), r);
            xs.add(x);
            ys.add(number(row[targetIdx], target, r));
        }
        if (xs.size() < MIN_ROWS) {
            throw new TrainingDataException("Only " + xs.size() + " complete rows remain after removing rows with missing values; at least "
                    + MIN_ROWS + " are needed.");
        }
        return new Prepared(xs.toArray(double[][]::new), ys.stream().mapToDouble(Double::doubleValue).toArray(), dropped);
    }

    static Split split(Prepared data, double testRatio, long seed, int featureCount) {
        int n = data.y().length;
        int testSize = (int) Math.round(n * testRatio);
        if (testSize < 2) throw new TrainingDataException("The test set would have fewer than 2 rows. Use more data.");
        if (n - testSize < featureCount + 2) {
            throw new TrainingDataException("Not enough training rows for " + featureCount + " feature(s). Use more data or fewer features.");
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        Collections.shuffle(order, new Random(seed));
        int trainSize = n - testSize;
        double[][] trainX = new double[trainSize][], testX = new double[testSize][];
        double[] trainY = new double[trainSize], testY = new double[testSize];
        for (int i = 0; i < n; i++) {
            int src = order.get(i);
            if (i < trainSize) { trainX[i] = data.x()[src]; trainY[i] = data.y()[src]; }
            else { testX[i - trainSize] = data.x()[src]; testY[i - trainSize] = data.y()[src]; }
        }
        return new Split(trainX, trainY, testX, testY);
    }

    private static double number(String value, String column, int rowIndex) {
        Double d = CsvTable.parseNumber(value);
        if (d == null) {
            throw new TrainingDataException("Column '" + column + "' has a non-numeric value '" + abbreviate(value) + "' in data row "
                    + (rowIndex + 1) + ". Linear regression only works with numeric columns.");
        }
        return d;
    }

    private static List<String> concat(List<String> list, String item) {
        List<String> out = new ArrayList<>(list);
        out.add(item);
        return out;
    }

    private static String abbreviate(String s) {
        return s.length() > 30 ? s.substring(0, 30) + "..." : s;
    }

    private static double[] predictAll(LinearRegression model, double[][] x) {
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) out[i] = model.predict(x[i]);
        return out;
    }

    private static double mean(double[] v) {
        double s = 0;
        for (double d : v) s += d;
        return s / v.length;
    }

    private static Double finiteOrNull(double v) {
        return Double.isFinite(v) ? v : null;
    }
}
