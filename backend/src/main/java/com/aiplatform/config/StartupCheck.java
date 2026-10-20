package com.aiplatform.config;

import java.util.List;
import java.util.Map;

/**
 * Runs before Spring starts. Prints a short, readable summary of the deployment settings (never the
 * secret values) and fixes common copy-paste mistakes in environment variables: spaces, quotes or
 * backticks around a value. Cloud logs bury the real database error deep in a stack trace, so this
 * makes mistakes like a leftover "HOST:PORT" placeholder obvious.
 */
public final class StartupCheck {
    private static final List<String> CLEANED = List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD", "AI_PROVIDER",
            "AI_API_KEY", "AI_MODEL", "AI_BASE_URL", "APP_ACCESS_KEY", "CORS_ALLOWED_ORIGINS");

    private StartupCheck() {}

    public static void run() {
        Map<String, String> env = System.getenv();
        for (String name : env.keySet()) {
            if (name.contains("`") || name.contains("\"") || !name.equals(name.strip())) {
                warn("Environment variable name [" + name + "] contains backticks, quotes or spaces. "
                        + "Rename it to [" + name.replaceAll("[`\"\\s]", "") + "].");
            }
        }
        for (String name : CLEANED) {
            String value = env.get(name);
            if (value == null) continue;
            String clean = clean(value);
            if (!clean.equals(value)) {
                System.setProperty(name, clean);   // system properties take priority over environment variables
                warn(name + " had extra spaces, quotes or backticks around it. They were removed automatically.");
            }
        }

        String url = value("DB_URL");
        if (url != null) {
            if (url.contains("HOST:PORT") || url.contains("AIVEN_HOST") || url.contains("AIVEN_PORT")) {
                warn("DB_URL still contains the example placeholder HOST:PORT. Replace it with your real database host and port.");
            }
            System.out.println("[ResearchAI] Database: " + url.replaceAll("^jdbc:mysql://", "").replaceAll("\\?.*$", "")
                    + " (user " + value("DB_USERNAME") + ", password " + describe(value("DB_PASSWORD")) + ")");
        }
        String provider = value("AI_PROVIDER");
        if (provider != null) {
            System.out.println("[ResearchAI] AI provider: " + provider + ", model " + value("AI_MODEL")
                    + ", API key " + describe(value("AI_API_KEY")));
        }
        if (value("APP_ACCESS_KEY") != null) System.out.println("[ResearchAI] Access key: " + describe(value("APP_ACCESS_KEY")));
        if (value("CORS_ALLOWED_ORIGINS") != null) System.out.println("[ResearchAI] Allowed website(s): " + value("CORS_ALLOWED_ORIGINS"));
    }

    static String clean(String value) {
        String v = value.strip();
        while (v.length() >= 2 && (v.startsWith("`") && v.endsWith("`") || v.startsWith("\"") && v.endsWith("\"")
                || v.startsWith("'") && v.endsWith("'"))) {
            v = v.substring(1, v.length() - 1).strip();
        }
        return v;
    }

    private static String value(String name) {
        String v = System.getProperty(name);
        return v != null ? v : System.getenv(name);
    }

    /** Shows only whether a secret is set and how long it is, never the secret itself. */
    private static String describe(String secret) {
        return secret == null || secret.isEmpty() ? "NOT SET" : "set (" + secret.length() + " characters)";
    }

    private static void warn(String message) {
        System.out.println("[ResearchAI] WARNING: " + message);
    }
}
