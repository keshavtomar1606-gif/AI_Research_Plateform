package com.aiplatform.service;

import com.aiplatform.dto.ChatMessage;
import java.util.List;

/** An AI chat backend: local Ollama, or a hosted OpenAI-compatible API such as Groq. */
public interface ChatProvider {
    String SYSTEM_PROMPT = "You are ResearchAI, a helpful assistant inside a student AI research "
            + "platform. Give clear, accurate, concise answers. Use simple Markdown (headings, lists, **bold**, code blocks) "
            + "when it helps. If you are not sure about something, say so.";

    /** Sends the conversation and returns the assistant's reply, or throws ResponseStatusException with a clear message. */
    String chat(List<ChatMessage> conversation);

    /** Quick availability check used by the health endpoint. */
    boolean isReachable();

    String model();

    /** Short provider name shown in the UI, e.g. "ollama" or "groq". */
    String name();
}
