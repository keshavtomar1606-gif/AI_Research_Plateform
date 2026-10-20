package com.aiplatform.controller;

import com.aiplatform.dto.ChatMessage;
import com.aiplatform.dto.ChatRequest;
import com.aiplatform.service.AiRateLimiter;
import com.aiplatform.service.ChatProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    /** Only the most recent messages are sent to the model to keep answers fast. */
    private static final int CONTEXT_MESSAGES = 20;

    private final ChatProvider ai;
    private final AiRateLimiter rateLimiter;

    public AiController(ChatProvider ai, AiRateLimiter rateLimiter) {
        this.ai = ai;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@Valid @RequestBody ChatRequest request, HttpServletRequest http) {
        List<ChatMessage> conversation;
        if (request.messages() != null && !request.messages().isEmpty()) {
            conversation = request.messages();
            if (!"user".equals(conversation.get(conversation.size() - 1).role())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The last message must be from the user.");
            }
            if (conversation.size() > CONTEXT_MESSAGES) {
                conversation = conversation.subList(conversation.size() - CONTEXT_MESSAGES, conversation.size());
            }
        } else if (request.prompt() != null && !request.prompt().isBlank()) {
            conversation = List.of(new ChatMessage("user", request.prompt().trim()));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type a message first.");
        }
        rateLimiter.check(http.getRemoteAddr());
        return Map.of("response", ai.chat(conversation), "model", ai.model(), "provider", ai.name());
    }
}
