package com.aiplatform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Either "prompt" (single question, kept for compatibility) or "messages" (the conversation so far,
 * ending with the newest user message) must be provided.
 */
public record ChatRequest(
        @Size(max = 4000, message = "Message is too long (max 4000 characters)") String prompt,
        @Valid @Size(max = 40, message = "Conversation is too long; start a new chat") List<ChatMessage> messages) {
}
