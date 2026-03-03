package com.example.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GatewayErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public GatewayErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        String correlationId = CorrelationIdFilter.resolveCorrelationId(exchange);
        String path = exchange.getRequest().getPath().value();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        payload.put("correlationId", correlationId);
        payload.put("path", path);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException ex) {
            String fallback = "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Unable to serialize error\","
                    + "\"correlationId\":\"" + correlationId + "\",\"path\":\"" + path + "\"}";
            body = fallback.getBytes(StandardCharsets.UTF_8);
        }

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
