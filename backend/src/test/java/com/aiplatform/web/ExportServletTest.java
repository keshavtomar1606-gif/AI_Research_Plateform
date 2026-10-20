package com.aiplatform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.dao.ExperimentDao;
import com.aiplatform.model.Experiment;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Starts the real embedded Tomcat on a random port and calls the servlet over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:servlettest;MODE=MySQL;DB_CLOSE_DELAY=-1")
class ExportServletTest {

    @LocalServerPort int port;
    @Autowired ExperimentDao experiments;
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void downloadsExperimentsAsCsv() throws Exception {
        experiments.insert(new Experiment(null, "Sweep, part 1", "Linear Regression", "=1+1", "Completed", null));
        HttpResponse<String> response = get("/export/experiments.csv");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"));
        assertEquals("attachment; filename=\"experiments.csv\"", response.headers().firstValue("Content-Disposition").orElse(""));
        String body = response.body();
        assertTrue(body.contains("id,name,model,metric,status,created_at"));
        assertTrue(body.contains("\"Sweep, part 1\""));   // comma inside a value is quoted
        assertTrue(body.contains(",'=1+1,"));             // spreadsheet formula is neutralised
    }

    @Test
    void downloadsTrainingResultsAndRejectsUnknownFiles() throws Exception {
        HttpResponse<String> training = get("/export/training-results.csv");
        assertEquals(200, training.statusCode());
        assertTrue(training.body().contains("id,type,model,dataset,status"));
        assertEquals(404, get("/export/passwords.csv").statusCode());
    }

    @Test
    void csvCellEscaping() {
        assertEquals("", ExportServlet.cell(null));
        assertEquals("plain", ExportServlet.cell("plain"));
        assertEquals("\"say \"\"hi\"\"\"", ExportServlet.cell("say \"hi\""));
        assertEquals("'@SUM(A1)", ExportServlet.cell("@SUM(A1)"));
        assertEquals("-3.5", ExportServlet.cell(-3.5));   // real negative numbers are left alone
    }
}
