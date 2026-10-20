package com.aiplatform.config;

import com.aiplatform.service.ChatProvider;
import com.aiplatform.service.OllamaService;
import com.aiplatform.service.OpenAiCompatibleService;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the AI provider from AI_PROVIDER:
 *   ollama (default)  - local Ollama, no key, used on your laptop
 *   groq              - Groq's free hosted API, needs AI_API_KEY (for cloud deployment)
 *   openai-compatible - any other OpenAI-format API, set AI_BASE_URL, AI_API_KEY and AI_MODEL
 */
@Configuration
public class AiConfig {
    static final String GROQ_URL = "https://api.groq.com/openai/v1";

    @Bean
    ChatProvider chatProvider(@Value("${app.ai.provider}") String provider,
                              @Value("${app.ollama.base-url}") String ollamaUrl,
                              @Value("${app.ollama.model}") String ollamaModel,
                              @Value("${app.ollama.timeout-seconds}") int ollamaTimeout,
                              @Value("${app.ai.base-url:}") String apiUrl,
                              @Value("${app.ai.api-key:}") String apiKey,
                              @Value("${app.ai.model}") String apiModel) {
        return switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "ollama" -> new OllamaService(ollamaUrl, ollamaModel, ollamaTimeout);
            case "groq" -> new OpenAiCompatibleService("groq", apiUrl.isBlank() ? GROQ_URL : apiUrl, apiKey, apiModel, 60);
            case "openai-compatible" -> {
                if (apiUrl.isBlank()) throw new IllegalStateException("AI_PROVIDER=openai-compatible needs AI_BASE_URL.");
                yield new OpenAiCompatibleService("api", apiUrl, apiKey, apiModel, 60);
            }
            default -> throw new IllegalStateException("Unknown AI_PROVIDER '" + provider + "'. Use ollama, groq or openai-compatible.");
        };
    }
}
