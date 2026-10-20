package com.aiplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiResearchPlatformApplication {
    public static void main(String[] args) {
        com.aiplatform.config.StartupCheck.run();
        SpringApplication.run(AiResearchPlatformApplication.class, args);
    }
}
