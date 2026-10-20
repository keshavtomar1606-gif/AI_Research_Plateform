package com.aiplatform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.dto.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OllamaServiceTest {

    @Test
    void reportsServiceUnavailableWhenOllamaIsNotRunning() {
        // Port 1 is never an Ollama server, so the connection is refused.
        OllamaService service = new OllamaService("http://127.0.0.1:1", "llama3.2:3b", 2);
        var e = assertThrows(ResponseStatusException.class, () -> service.chat(List.of(new ChatMessage("user", "hi"))));
        assertEquals(503, e.getStatusCode().value());
        assertTrue(e.getReason().contains("Cannot reach Ollama"));
        assertFalse(service.isReachable());
    }
}
