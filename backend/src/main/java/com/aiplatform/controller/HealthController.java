package com.aiplatform.controller;

import com.aiplatform.config.AccessKeyFilter;
import com.aiplatform.service.ChatProvider;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final DataSource dataSource;
    private final ChatProvider ai;
    private final AccessKeyFilter accessKey;

    public HealthController(DataSource dataSource, ChatProvider ai, AccessKeyFilter accessKey) {
        this.dataSource = dataSource;
        this.ai = ai;
        this.accessKey = accessKey;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("application", "AI Research Platform");
        result.put("database", databaseUp() ? "UP" : "DOWN");
        result.put("ai", ai.isReachable() ? "UP" : "DOWN");
        result.put("aiProvider", ai.name());
        result.put("model", ai.model());
        result.put("accessKeyRequired", accessKey.enabled());
        return result;
    }

    private boolean databaseUp() {
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
