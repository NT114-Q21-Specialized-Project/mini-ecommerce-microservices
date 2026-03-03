package com.example.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class ApiVersionEnforcementFilter implements WebFilter, Ordered {

    private final RouteAuthorizationEvaluator evaluator;
    private final GatewayErrorResponseWriter errorWriter;

    public ApiVersionEnforcementFilter(
            RouteAuthorizationEvaluator evaluator,
            GatewayErrorResponseWriter errorWriter
    ) {
        this.evaluator = evaluator;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!evaluator.isVersionPrefixRequired()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        if (!evaluator.isApiPath(path)) {
            return chain.filter(exchange);
        }

        if (evaluator.isVersionedApiPath(path)) {
            return chain.filter(exchange);
        }

        return errorWriter.write(
                exchange,
                HttpStatus.NOT_FOUND,
                "API_VERSION_REQUIRED",
                "Route must use version prefix " + evaluator.requiredPrefix()
        );
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
