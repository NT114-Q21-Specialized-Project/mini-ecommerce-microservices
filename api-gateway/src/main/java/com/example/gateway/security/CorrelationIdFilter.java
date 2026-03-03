package com.example.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdFilter implements WebFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = resolveCorrelationId(exchange);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        exchange.getAttributes().put(CORRELATION_ID_ATTRIBUTE, correlationId);

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    public static String resolveCorrelationId(ServerWebExchange exchange) {
        Object existingAttr = exchange.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (existingAttr instanceof String value && !value.isBlank()) {
            return value;
        }

        String fromRequest = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (fromRequest != null && !fromRequest.isBlank()) {
            return fromRequest.trim();
        }

        return UUID.randomUUID().toString();
    }
}
