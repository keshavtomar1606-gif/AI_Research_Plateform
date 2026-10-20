package com.aiplatform.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Limits AI requests per visitor and in total (per minute), so a public deployment cannot
 * use up the free AI quota. Counts are kept in memory and reset when the server restarts.
 */
@Component
public class AiRateLimiter {
    private static final long WINDOW_MS = 60_000;
    private static final String GLOBAL = "*";

    private final int perClient;
    private final int global;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public AiRateLimiter(@Value("${app.ai.rate-limit-per-minute}") int perClient,
                         @Value("${app.ai.global-limit-per-minute}") int global) {
        this.perClient = perClient;
        this.global = global;
    }

    public void check(String clientId) {
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (hits.size() > 10_000) hits.clear();
            Deque<Long> mine = window(clientId, now);
            Deque<Long> all = window(GLOBAL, now);
            if (mine.size() >= perClient) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many AI messages. Please wait a minute (limit " + perClient + " per minute).");
            }
            if (all.size() >= global) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "The AI is busy right now. Please try again in a minute.");
            }
            mine.addLast(now);
            all.addLast(now);
        }
    }

    private Deque<Long> window(String key, long now) {
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() >= WINDOW_MS) q.pollFirst();
        return q;
    }
}
