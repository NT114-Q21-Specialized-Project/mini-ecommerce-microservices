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
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final boolean jwtEnabled;
    private final SecretKey jwtSecretKey;

    public JwtAuthenticationFilter(
            @Value("${security.jwt.enabled:true}") boolean jwtEnabled,
            @Value("${security.jwt.secret}") String jwtSecret
    ) {
        this.jwtEnabled = jwtEnabled;
        this.jwtSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (!jwtEnabled || isPublicRoute(request)) {
            return chain.filter(exchange);
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
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
            return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        String userId = claims.getSubject();
        String role = claims.get("role", String.class);

        if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
            return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Token missing required claims");
        }

        if (!isAuthorized(request, role)) {
            return reject(exchange.getResponse(), HttpStatus.FORBIDDEN, "Forbidden");
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

    private boolean isPublicRoute(ServerHttpRequest request) {
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }

        if ("/actuator/health".equals(path) || "/actuator/info".equals(path)) {
            return true;
        }

        if ("/api/users/health".equals(path)) {
            return true;
        }

        if ("/api/users/login".equals(path) && HttpMethod.POST.equals(method)) {
            return true;
        }

        if ("/api/users".equals(path) && HttpMethod.POST.equals(method)) {
            return true;
        }

        return path.startsWith("/api/products") && HttpMethod.GET.equals(method);
    }

    private boolean isAuthorized(ServerHttpRequest request, String role) {
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        if (path.matches("^/api/products/[^/]+/(decrease-stock|increase-stock)$")) {
            return false;
        }

        if (path.startsWith("/api/products") && HttpMethod.POST.equals(method)) {
            return "SELLER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        }

        if (path.startsWith("/api/orders") && HttpMethod.POST.equals(method)) {
            return "CUSTOMER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        }

        if (path.startsWith("/api/orders") && HttpMethod.GET.equals(method)) {
            if (path.startsWith("/api/orders/outbox")) {
                return "ADMIN".equalsIgnoreCase(role);
            }
            return "CUSTOMER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        }

        if (path.matches("^/api/orders/[^/]+/cancel$") && HttpMethod.PATCH.equals(method)) {
            return "CUSTOMER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        }

        return true;
    }

    private Mono<Void> reject(ServerHttpResponse response, HttpStatus status, String message) {
        response.setStatusCode(status);
        byte[] payload = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
    }
}
