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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Talks to a hosted AI service that uses the OpenAI chat-completions format, e.g. Groq
 * (https://api.groq.com/openai/v1), OpenRouter or Gemini's OpenAI-compatible endpoint.
 * The API key stays on the server and is never sent to the browser or written to logs.
 */
public class OpenAiCompatibleService implements ChatProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleService.class);
    private static final long STATUS_CACHE_MS = 60_000;

    private final String name;
    private final String model;
    private final RestClient chatClient;
    private final RestClient statusClient;
    private volatile long statusCheckedAt;
    private volatile boolean lastStatus;

    public OpenAiCompatibleService(String name, String baseUrl, String apiKey, String model, int timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI_PROVIDER=" + name + " needs an API key. Set the AI_API_KEY environment variable.");
        }
        this.name = name;
        this.model = model;
        this.chatClient = client(baseUrl, apiKey, Duration.ofSeconds(5), Duration.ofSeconds(timeoutSeconds));
        this.statusClient = client(baseUrl, apiKey, Duration.ofSeconds(3), Duration.ofSeconds(5));
    }

    private static RestClient client(String baseUrl, String apiKey, Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey.trim()).build();
    }

    @Override public String model() { return model; }
    @Override public String name() { return name; }

    @Override
    public String chat(List<ChatMessage> conversation) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (ChatMessage m : conversation) messages.add(Map.of("role", m.role(), "content", m.content()));

        Map<?, ?> response;
        try {
            response = chatClient.post().uri("/chat/completions")
                    .body(Map.of("model", model, "messages", messages, "temperature", 0.7))
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.warn("AI API rejected the API key (HTTP {})", e.getStatusCode().value());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI API key is missing or invalid. Check AI_API_KEY on the server.");
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "The free AI quota is used up for now. Please wait a minute and try again.");
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI model '" + model + "' was not found. Set AI_MODEL to a model your provider offers.");
        } catch (HttpClientErrorException e) {
            log.warn("AI API returned HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI service rejected the request (HTTP " + e.getStatusCode().value() + "). The model name may be wrong or retired.");
        } catch (HttpServerErrorException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI service is having problems. Try again in a moment.");
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException t && String.valueOf(t.getMessage()).contains("Read timed out")) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "The AI service took too long to answer. Try again.");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot reach the AI service. Check the server's internet connection.");
        } catch (RestClientException e) {
            log.warn("AI request failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI service returned an error. Try again.");
        }

        Object content = null;
        if (response != null && response.get("choices") instanceof List<?> choices && !choices.isEmpty()
                && choices.get(0) instanceof Map<?, ?> choice && choice.get("message") instanceof Map<?, ?> message) {
            content = message.get("content");
        }
        if (content == null || content.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI model returned an empty response. Try again.");
        }
        return content.toString().trim();
    }

    /** Cached for a minute so health checks don't use up the free API quota. */
    @Override
    public boolean isReachable() {
        long now = System.currentTimeMillis();
        if (now - statusCheckedAt < STATUS_CACHE_MS) return lastStatus;
        boolean ok;
        try {
            statusClient.get().uri("/models").retrieve().toBodilessEntity();
            ok = true;
        } catch (RestClientException e) {
            ok = false;
        }
        lastStatus = ok;
        statusCheckedAt = now;
        return ok;
    }
}
