package com.example.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final boolean jwtEnabled;
    private final SecretKey jwtSecretKey;
    private final RouteAuthorizationEvaluator authorizationEvaluator;
    private final GatewayErrorResponseWriter errorWriter;

    public JwtAuthenticationFilter(
            @Value("${security.jwt.enabled:true}") boolean jwtEnabled,
            @Value("${security.jwt.secret}") String jwtSecret,
            RouteAuthorizationEvaluator authorizationEvaluator,
            GatewayErrorResponseWriter errorWriter
    ) {
        this.jwtEnabled = jwtEnabled;
        this.jwtSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.authorizationEvaluator = authorizationEvaluator;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getPath().value();

        if (!jwtEnabled || HttpMethod.OPTIONS.equals(method) || authorizationEvaluator.isPublicRoute(path, method)) {
            return chain.filter(exchange);
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing or invalid Authorization header");
        }

        String token = authorization.substring("Bearer ".length()).trim();
        Claims claims;

        try {
            claims = Jwts.parser()
                    .verifyWith(jwtSecretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid or expired token");
        }

        String userId = claims.getSubject();
        String role = claims.get("role", String.class);

        if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token missing required claims");
        }

        RouteAuthorizationEvaluator.AuthorizationDecision decision =
                authorizationEvaluator.authorize(path, method, role, userId);
        if (!decision.allowed()) {
            return reject(exchange, HttpStatus.FORBIDDEN, decision.code(), decision.message());
        }

        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", userId)
                .header("X-User-Role", role)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        return errorWriter.write(exchange, status, code, message);
    }
}
