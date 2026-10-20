package com.aiplatform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Optional protection for a public deployment. When APP_ACCESS_KEY is set, every request that
 * changes data or uses the AI (POST/PUT/DELETE) must send the same value in the X-Access-Key header.
 * Viewing data (GET) stays open so visitors can look around.
 * This is a shared demo password, not a full user-account system.
 */
@Component
public class AccessKeyFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Access-Key";
    private final byte[] accessKey;

    public AccessKeyFilter(@Value("${app.access-key:}") String accessKey) {
        this.accessKey = accessKey == null || accessKey.isBlank() ? null : accessKey.trim().getBytes(StandardCharsets.UTF_8);
    }

    public boolean enabled() { return accessKey != null; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        return accessKey == null || !request.getRequestURI().startsWith("/api/")
                || method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        if (given != null && MessageDigest.isEqual(accessKey, given.trim().getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String error = given == null || given.isBlank() ? "An access key is required to make changes." : "Wrong access key.";
        response.getWriter().write("{\"error\":\"" + error + "\",\"code\":\"ACCESS_KEY_REQUIRED\"}");
    }
}
