package com.example.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginProtectionFilter implements WebFilter, Ordered {

    private static final String LOGIN_PATH = "/api/v1/users/login";

    private final LoginProtectionProperties properties;
    private final GatewayErrorResponseWriter errorWriter;
    private final Map<String, LoginAttemptState> states = new ConcurrentHashMap<>();

    public LoginProtectionFilter(LoginProtectionProperties properties, GatewayErrorResponseWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled() || !isLoginRequest(exchange)) {
            return chain.filter(exchange);
        }

        String key = resolveClientKey(exchange);
        LoginAttemptState state = states.computeIfAbsent(key, ignored -> new LoginAttemptState());
        long now = System.currentTimeMillis();

        if (isBlocked(state, now)) {
            return errorWriter.write(
                    exchange,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "LOGIN_TEMPORARILY_BLOCKED",
                    "Too many failed login attempts. Please retry later."
            );
        }

        if (exceedsRateLimit(state, now)) {
            return errorWriter.write(
                    exchange,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMITED",
                    "Too many login requests. Please slow down."
            );
        }

        return chain.filter(exchange)
                .doOnSuccess(ignored -> onResponse(state, exchange.getResponse().getStatusCode()))
                .doOnError(ignored -> onResponse(state, HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean isLoginRequest(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        return HttpMethod.POST.equals(method) && LOGIN_PATH.equals(path);
    }

    private boolean exceedsRateLimit(LoginAttemptState state, long now) {
        synchronized (state) {
            long windowMs = Math.max(1, properties.getRateLimitWindowSeconds()) * 1000L;
            if (now - state.requestWindowStartMs >= windowMs) {
                state.requestWindowStartMs = now;
                state.requestCount = 0;
            }

            state.requestCount++;
            return state.requestCount > Math.max(1, properties.getMaxRequestsPerWindow());
        }
    }

    private boolean isBlocked(LoginAttemptState state, long now) {
        synchronized (state) {
            return state.blockedUntilMs > now;
        }
    }

    private void onResponse(LoginAttemptState state, HttpStatusCode statusCode) {
        int status = statusCode == null ? 200 : statusCode.value();
        long now = System.currentTimeMillis();

        synchronized (state) {
            if (status >= 200 && status < 300) {
                // Successful authentication should clear transient rate-limit pressure
                // so legitimate repeated logins do not get locked by stale counters.
                state.requestWindowStartMs = now;
                state.requestCount = 0;
                state.failedAttempts = 0;
                state.failedWindowStartMs = now;
                return;
            }

            if (status != HttpStatus.UNAUTHORIZED.value()) {
                return;
            }

            long failedWindowMs = Math.max(1, properties.getFailedAttemptWindowSeconds()) * 1000L;
            if (now - state.failedWindowStartMs >= failedWindowMs) {
                state.failedWindowStartMs = now;
                state.failedAttempts = 0;
            }

            state.failedAttempts++;
            if (state.failedAttempts >= Math.max(1, properties.getMaxFailedAttempts())) {
                long blockMs = Math.max(1, properties.getBlockDurationSeconds()) * 1000L;
                state.blockedUntilMs = now + blockMs;
                state.failedAttempts = 0;
                state.failedWindowStartMs = now;
            }
        }
    }

    private String resolveClientKey(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return parts[0].trim();
            }
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }

    private static final class LoginAttemptState {
        private long requestWindowStartMs = System.currentTimeMillis();
        private int requestCount;
        private long failedWindowStartMs = System.currentTimeMillis();
        private int failedAttempts;
        private long blockedUntilMs;
    }
}
