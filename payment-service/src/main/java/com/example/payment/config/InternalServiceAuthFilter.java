package com.example.payment.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final String expectedToken;
    private final ObjectMapper objectMapper;

    public InternalServiceAuthFilter(
            @Value("${security.internal.token:}") String expectedToken,
            ObjectMapper objectMapper
    ) {
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean isProtectedPath = "/payments/pay".equals(path) || "/payments/refund".equals(path);
        return !isProtectedPath || !HttpMethod.POST.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        response.setHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);

        if (expectedToken.isBlank()) {
            reject(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "INTERNAL_AUTH_MISCONFIG", "Internal authentication is not configured", correlationId);
            return;
        }

        String providedToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (providedToken == null || providedToken.isBlank()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "INTERNAL_AUTH_REQUIRED", "Missing internal authentication token", correlationId);
            return;
        }

        if (!secureTokenMatch(providedToken.trim(), expectedToken)) {
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                    "INTERNAL_AUTH_FAILED", "Invalid internal authentication token", correlationId);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean secureTokenMatch(String provided, String expected) {
        if (provided.isEmpty() || expected.isEmpty() || provided.length() != expected.length()) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            return java.util.UUID.randomUUID().toString();
        }
        return correlationId.trim();
    }

    private void reject(HttpServletResponse response, int status, String code, String message, String correlationId) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", Map.of(
                "code", code,
                "message", message
        ));
        payload.put("correlationId", correlationId);
        response.getWriter().write(objectMapper.writeValueAsString(payload));
    }
}
