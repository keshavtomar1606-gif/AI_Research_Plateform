package com.aiplatform.web;

import com.aiplatform.dao.ExperimentDao;
import com.aiplatform.exception.ResearchAiException;
import com.aiplatform.model.Experiment;
import com.aiplatform.model.TrainingJob;
import com.aiplatform.repository.TrainingJobRepository;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A classic Java Servlet (registered in ServletConfig at /export/*) that lets the browser download data as CSV:
 *   GET /export/training-results.csv  - every training run with its metrics
 *   GET /export/experiments.csv       - all experiment notes
 * It writes the HTTP response itself: status, headers (Content-Type, Content-Disposition) and body.
 */
public class ExportServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(ExportServlet.class);

    private final transient TrainingJobRepository trainingJobs;
    private final transient ExperimentDao experiments;

    public ExportServlet(TrainingJobRepository trainingJobs, ExperimentDao experiments) {
        this.trainingJobs = trainingJobs;
        this.experiments = experiments;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String file = request.getPathInfo() == null ? "" : request.getPathInfo().substring(1);
        try {
            switch (file) {
                case "training-results.csv" -> sendCsv(response, file, trainingCsv(trainingJobs.findAllByOrderByIdDesc()));
                case "experiments.csv" -> sendCsv(response, file, experimentsCsv(experiments.findAll()));
                default -> response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "Unknown export. Use /export/training-results.csv or /export/experiments.csv");
            }
        } catch (ResearchAiException | org.springframework.dao.DataAccessException e) {
            log.error("CSV export failed", e);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Database error. Try again later.");
        }
    }

    private static void sendCsv(HttpServletResponse response, String fileName, String csv) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "no-store");
        try (PrintWriter out = response.getWriter()) {
            out.write('﻿');   // byte-order mark so Excel opens UTF-8 correctly
            out.write(csv);
        }
    }

    static String trainingCsv(List<TrainingJob> jobs) {
        StringBuilder csv = new StringBuilder("id,type,model,dataset,status,target,features,train_rows,test_rows,mae,rmse,r2,error\n");
        for (TrainingJob j : jobs) {
            row(csv, j.getId(), j.isMlJob() ? "ML_TRAINING" : "RECORD", j.getModel(), j.getDataset(), j.getStatus(),
                    j.getTargetColumn(), j.getFeatureColumns() == null ? "" : j.getFeatureColumns().replace('\t', ' '),
                    j.getTrainRows(), j.getTestRows(), number(j.getMae()), number(j.getRmse()), number(j.getR2()), j.getErrorMessage());
        }
        return csv.toString();
    }

    static String experimentsCsv(List<Experiment> list) {
        StringBuilder csv = new StringBuilder("id,name,model,metric,status,created_at\n");
        for (Experiment e : list) row(csv, e.id(), e.name(), e.model(), e.metric(), e.status(), e.createdAt());
        return csv.toString();
    }

    private static void row(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(cell(values[i]));
        }
        csv.append('\n');
    }

    /** Quotes a value when needed, and stops spreadsheet formula injection (cells starting with = + - @). */
    static String cell(Object value) {
        if (value == null) return "";
        String text = value.toString();
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !(value instanceof Number)) text = "'" + text;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String number(Double value) {
        return value == null ? null : String.format(Locale.ROOT, "%.4f", value);
    }
}
