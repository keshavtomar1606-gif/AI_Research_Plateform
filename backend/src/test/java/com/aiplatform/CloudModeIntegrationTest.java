package com.aiplatform;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.service.OpenAiCompatibleService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The app configured like a public cloud deployment: Groq-style API, access key, low AI rate limit. */
@SpringBootTest(properties = {
        "app.ai.provider=groq", "app.ai.api-key=test-key", "app.ai.model=llama-test",
        "app.access-key=demo-secret", "app.ai.rate-limit-per-minute=3",
        "app.cors.allowed-origins=https://researchai.netlify.app/",
        "spring.datasource.url=jdbc:h2:mem:clouddb;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class CloudModeIntegrationTest {

    static volatile String mode = "ok";
    static volatile String lastAuth = "";
    static volatile String lastBody = "";
    static final HttpServer FAKE_API = start();

    static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/models", ex -> reply(ex, "Bearer test-key".equals(ex.getRequestHeaders().getFirst("Authorization")) ? 200 : 401, "{\"data\":[]}"));
            server.createContext("/v1/chat/completions", ex -> {
                lastAuth = ex.getRequestHeaders().getFirst("Authorization");
                lastBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                switch (mode) {
                    case "bad-key" -> reply(ex, 401, "{\"error\":{\"message\":\"Invalid API Key\"}}");
                    case "quota" -> reply(ex, 429, "{\"error\":{\"message\":\"Rate limit reached\"}}");
                    default -> reply(ex, 200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hi from Groq\"}}]}");
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void reply(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @DynamicPropertySource
    static void apiUrl(DynamicPropertyRegistry registry) {
        registry.add("app.ai.base-url", () -> "http://127.0.0.1:" + FAKE_API.getAddress().getPort() + "/v1");
    }

    @AfterAll
    static void stop() {
        FAKE_API.stop(0);
    }

    @Autowired MockMvc mvc;
    static final String ORIGIN = "https://researchai.netlify.app";
    static final String CHAT = "{\"prompt\":\"hello\"}";

    @Test
    void healthShowsCloudConfiguration() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ai").value("UP"))
                .andExpect(jsonPath("$.aiProvider").value("groq"))
                .andExpect(jsonPath("$.model").value("llama-test"))
                .andExpect(jsonPath("$.accessKeyRequired").value(true))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @Test
    void accessKeyProtectsChangesButNotReading() throws Exception {
        String project = "{\"title\":\"Cloud project\"}";
        mvc.perform(post("/api/projects").header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON).content(project))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_KEY_REQUIRED"))
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));   // browser can read the error
        mvc.perform(post("/api/projects").header("X-Access-Key", "wrong").contentType(MediaType.APPLICATION_JSON).content(project))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Wrong access key."));
        mvc.perform(post("/api/projects").header("X-Access-Key", "demo-secret").contentType(MediaType.APPLICATION_JSON).content(project))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/projects")).andExpect(status().isOk());
        mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON).content(CHAT)).andExpect(status().isUnauthorized());
        mvc.perform(options("/api/projects").header("Origin", ORIGIN).header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-access-key"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }

    @Test
    void chatUsesApiKeyServerSideAndHandlesErrorsAndRateLimit() throws Exception {
        try {
            mode = "ok";
            mvc.perform(post("/api/ai/chat").header("X-Access-Key", "demo-secret").contentType(MediaType.APPLICATION_JSON).content(CHAT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.response").value("Hi from Groq"))
                    .andExpect(jsonPath("$.provider").value("groq"));
            assertEquals("Bearer test-key", lastAuth);
            assertTrue(lastBody.contains("\"model\":\"llama-test\"") && lastBody.contains("\"system\""));

            mode = "bad-key";
            mvc.perform(post("/api/ai/chat").header("X-Access-Key", "demo-secret").contentType(MediaType.APPLICATION_JSON).content(CHAT))
                    .andExpect(status().isBadGateway()).andExpect(jsonPath("$.error", containsString("API key")))
                    .andExpect(jsonPath("$.error", org.hamcrest.Matchers.not(containsString("test-key"))));

            mode = "quota";
            mvc.perform(post("/api/ai/chat").header("X-Access-Key", "demo-secret").contentType(MediaType.APPLICATION_JSON).content(CHAT))
                    .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error", containsString("quota")));

            // Limit is 3 per minute per visitor: the 4th request is refused before reaching the API.
            mode = "ok";
            mvc.perform(post("/api/ai/chat").header("X-Access-Key", "demo-secret").contentType(MediaType.APPLICATION_JSON).content(CHAT))
                    .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error", containsString("per minute")));
        } finally {
            mode = "ok";
        }
    }

    @Test
    void cloudProviderWithoutKeyRefusesToStart() {
        var e = assertThrows(IllegalStateException.class, () -> new OpenAiCompatibleService("groq", "http://x", " ", "m", 5));
        assertTrue(e.getMessage().contains("AI_API_KEY"));
    }
}
