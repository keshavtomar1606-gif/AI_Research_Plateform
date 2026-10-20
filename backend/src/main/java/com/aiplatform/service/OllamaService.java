package com.aiplatform.service;

import com.aiplatform.dto.ChatMessage;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Talks to the local Ollama server. No API key or paid service is involved. */
public class OllamaService implements ChatProvider {
    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);
    private final String baseUrl;
    private final String model;
    private final RestClient chatClient;
    private final RestClient statusClient;

    public OllamaService(String baseUrl, String model, int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.chatClient = client(baseUrl, Duration.ofSeconds(5), Duration.ofSeconds(timeoutSeconds));
        this.statusClient = client(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    private static RestClient client(String baseUrl, Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override public String model() { return model; }
    @Override public String name() { return "ollama"; }

    /** Sends the conversation to Ollama's /api/chat endpoint and returns the assistant's reply. */
    @Override
    public String chat(List<ChatMessage> conversation) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (ChatMessage m : conversation) messages.add(Map.of("role", m.role(), "content", m.content()));

        Map<?, ?> response;
        try {
            response = chatClient.post().uri("/api/chat")
                    .body(Map.of("model", model, "messages", messages, "stream", false))
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI model '" + model + "' is not installed. Run: ollama pull " + model);
        } catch (ResourceAccessException e) {
            // A read timeout means Ollama is running but slow; anything else means it is not reachable.
            if (e.getCause() instanceof SocketTimeoutException t && String.valueOf(t.getMessage()).contains("Read timed out")) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "The AI model took too long to answer. Try a shorter question.");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cannot reach Ollama at " + baseUrl + ". Start the Ollama app (or run 'ollama serve') and try again.");
        } catch (RestClientException e) {
            log.warn("Ollama request failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI service returned an error. Try again.");
        }

        Object message = response == null ? null : response.get("message");
        Object content = message instanceof Map<?, ?> m ? m.get("content") : null;
        if (content == null || content.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI model returned an empty response. Try again.");
        }
        return content.toString().trim();
    }

    @Override
    public boolean isReachable() {
        try {
            statusClient.get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }
}
