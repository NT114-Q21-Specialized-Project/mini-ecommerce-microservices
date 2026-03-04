package com.example.order.service;

import com.example.order.model.Order;
import com.example.order.util.SimpleCircuitBreaker;
import com.example.order.util.StructuredLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class OrderClientAdapter {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SagaStepRecorder sagaStepRecorder;
    private final StructuredLogger structuredLogger;

    private final String productServiceBaseUrl;
    private final String inventoryServiceBaseUrl;
    private final String paymentServiceBaseUrl;

    private final int sagaMaxAttempts;
    private final long sagaInitialBackoffMs;

    private final boolean chaosMode;
    private final double chaosLatencyProbability;
    private final double chaosErrorProbability;
    private final int chaosDelayMs;

    private final SimpleCircuitBreaker inventoryCircuitBreaker;
    private final SimpleCircuitBreaker paymentCircuitBreaker;

    public OrderClientAdapter(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            SagaStepRecorder sagaStepRecorder,
            StructuredLogger structuredLogger,
            @Value("${clients.product-service.base-url}") String productServiceBaseUrl,
            @Value("${clients.inventory-service.base-url:http://inventory-service:8080}") String inventoryServiceBaseUrl,
            @Value("${clients.payment-service.base-url:http://payment-service:8080}") String paymentServiceBaseUrl,
            @Value("${saga.retry.max-attempts:3}") int sagaMaxAttempts,
            @Value("${saga.retry.initial-backoff-ms:250}") long sagaInitialBackoffMs,
            @Value("${saga.circuit-breaker.failure-threshold:3}") int circuitBreakerFailureThreshold,
            @Value("${saga.circuit-breaker.open-duration-ms:4000}") long circuitBreakerOpenDurationMs,
            @Value("${chaos.mode:false}") boolean chaosMode,
            @Value("${chaos.latency.probability:0.0}") double chaosLatencyProbability,
            @Value("${chaos.error.probability:0.0}") double chaosErrorProbability,
            @Value("${chaos.delay.ms:0}") int chaosDelayMs
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.sagaStepRecorder = sagaStepRecorder;
        this.structuredLogger = structuredLogger;
        this.productServiceBaseUrl = trimTrailingSlash(productServiceBaseUrl);
        this.inventoryServiceBaseUrl = trimTrailingSlash(inventoryServiceBaseUrl);
        this.paymentServiceBaseUrl = trimTrailingSlash(paymentServiceBaseUrl);
        this.sagaMaxAttempts = Math.max(1, sagaMaxAttempts);
        this.sagaInitialBackoffMs = Math.max(100, sagaInitialBackoffMs);
        this.chaosMode = chaosMode;
        this.chaosLatencyProbability = clampProbability(chaosLatencyProbability);
        this.chaosErrorProbability = clampProbability(chaosErrorProbability);
        this.chaosDelayMs = Math.max(0, chaosDelayMs);
        this.inventoryCircuitBreaker = new SimpleCircuitBreaker(
                "inventory-service",
                circuitBreakerFailureThreshold,
                circuitBreakerOpenDurationMs
        );
        this.paymentCircuitBreaker = new SimpleCircuitBreaker(
                "payment-service",
                circuitBreakerFailureThreshold,
                circuitBreakerOpenDurationMs
        );
    }

    public ProductResponse getProduct(UUID productId, String correlationId) {
        String productUrl = productServiceBaseUrl + "/products/" + productId;
        HttpHeaders headers = buildServiceHeaders(correlationId, null);

        try {
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            var response = restTemplate.exchange(productUrl, HttpMethod.GET, requestEntity, ProductResponse.class);
            ProductResponse body = response.getBody();
            if (body == null) {
                throw new OrderWorkflowException(502, "BAD_PRODUCT_RESPONSE", "Invalid response from product-service");
            }
            return body;
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                throw new OrderWorkflowException(404, "PRODUCT_NOT_FOUND", "Product not found");
            }
            throw new OrderWorkflowException(502, "PRODUCT_SERVICE_ERROR", extractErrorMessage(e, "Product-service returned error"));
        } catch (ResourceAccessException e) {
            throw new OrderWorkflowException(502, "PRODUCT_SERVICE_UNAVAILABLE", "Product service unavailable");
        }
    }

    public void reserveInventory(Order order, String correlationId) {
        String endpoint = inventoryServiceBaseUrl + "/inventory/reserve";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("productId", order.getProductId());
        payload.put("quantity", order.getQuantity());

        executeWithRetry(
                "INVENTORY_RESERVE",
                order,
                correlationId,
                false,
                inventoryCircuitBreaker,
                () -> {
                    HttpHeaders headers = buildServiceHeaders(correlationId, order.getIdempotencyKey() + ":inventory:reserve");
                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
                    restTemplate.exchange(endpoint, HttpMethod.POST, requestEntity, Map.class);
                    return true;
                }
        );
    }

    public void releaseInventory(Order order, String correlationId, boolean compensation) {
        String endpoint = inventoryServiceBaseUrl + "/inventory/release";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("productId", order.getProductId());
        payload.put("quantity", order.getQuantity());

        executeWithRetry(
                "INVENTORY_RELEASE",
                order,
                correlationId,
                compensation,
                inventoryCircuitBreaker,
                () -> {
                    HttpHeaders headers = buildServiceHeaders(correlationId, order.getIdempotencyKey() + ":inventory:release");
                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
                    restTemplate.exchange(endpoint, HttpMethod.POST, requestEntity, Map.class);
                    return true;
                }
        );
    }

    public void capturePayment(Order order, String correlationId) {
        String endpoint = paymentServiceBaseUrl + "/payments/pay";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("userId", order.getUserId());
        payload.put("amount", order.getTotalAmount());
        payload.put("currency", "USD");

        executeWithRetry(
                "PAYMENT_PAY",
                order,
                correlationId,
                false,
                paymentCircuitBreaker,
                () -> {
                    HttpHeaders headers = buildServiceHeaders(correlationId, order.getIdempotencyKey() + ":payment:pay");
                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
                    restTemplate.exchange(endpoint, HttpMethod.POST, requestEntity, Map.class);
                    return true;
                }
        );
    }

    public void refundPayment(Order order, String correlationId, boolean compensation) {
        String endpoint = paymentServiceBaseUrl + "/payments/refund";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("amount", order.getTotalAmount());
        payload.put("currency", "USD");

        executeWithRetry(
                "PAYMENT_REFUND",
                order,
                correlationId,
                compensation,
                paymentCircuitBreaker,
                () -> {
                    HttpHeaders headers = buildServiceHeaders(correlationId, order.getIdempotencyKey() + ":payment:refund");
                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
                    restTemplate.exchange(endpoint, HttpMethod.POST, requestEntity, Map.class);
                    return true;
                }
        );
    }

    public String extractFailureReason(Exception ex) {
        if (ex == null) {
            return "Unknown error";
        }
        if (ex instanceof OrderWorkflowException workflowException) {
            return workflowException.getMessage();
        }
        if (ex instanceof HttpStatusCodeException statusException) {
            return extractErrorMessage(statusException, "Downstream request failed");
        }
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private <T> T executeWithRetry(
            String stepName,
            Order order,
            String correlationId,
            boolean compensation,
            SimpleCircuitBreaker circuitBreaker,
            Callable<T> action
    ) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= sagaMaxAttempts; attempt++) {
            Span.current().setAttribute("order_id", order.getId().toString());
            Span.current().setAttribute("saga_step", stepName);
            Span.current().setAttribute("retry_count", attempt - 1);
            Span.current().setAttribute("compensation", compensation);

            maybeInjectChaos(stepName, correlationId);

            try {
                T result = circuitBreaker.execute(action);
                sagaStepRecorder.record(
                        order.getId(),
                        stepName,
                        "SUCCESS",
                        attempt - 1,
                        compensation,
                        "completed",
                        correlationId
                );
                return result;
            } catch (Exception ex) {
                lastException = ex;
                boolean exhausted = attempt >= sagaMaxAttempts;
                String status = exhausted ? "FAILED" : "RETRY_FAILED";
                sagaStepRecorder.record(
                        order.getId(),
                        stepName,
                        status,
                        attempt,
                        compensation,
                        extractFailureReason(ex),
                        correlationId
                );

                structuredLogger.warn("order.saga.retry", Map.of(
                        "order_id", order.getId().toString(),
                        "step", stepName,
                        "attempt", attempt,
                        "max_attempts", sagaMaxAttempts,
                        "compensation", compensation,
                        "correlation_id", correlationId
                ));

                if (!exhausted) {
                    sleepWithBackoff(attempt);
                }
            }
        }

        throw toWorkflowException(lastException, stepName);
    }

    private void maybeInjectChaos(String stepName, String correlationId) {
        if (!chaosMode) {
            return;
        }

        if (chance(chaosLatencyProbability) && chaosDelayMs > 0) {
            try {
                Thread.sleep(chaosDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OrderWorkflowException(500, "INTERRUPTED", "Chaos delay interrupted");
            }
        }

        if (chance(chaosErrorProbability)) {
            structuredLogger.warn("order.chaos.failure", Map.of(
                    "step", stepName,
                    "correlation_id", correlationId
            ));
            throw new OrderWorkflowException(500, "CHAOS_FAILURE", "Injected failure by CHAOS_MODE");
        }
    }

    private boolean chance(double probability) {
        return ThreadLocalRandom.current().nextDouble(0.0, 1.0) < probability;
    }

    private void sleepWithBackoff(int attempt) {
        long backoffMs = sagaInitialBackoffMs * (1L << Math.max(0, attempt - 1));
        backoffMs = Math.min(backoffMs, 5000);
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrderWorkflowException(500, "INTERRUPTED", "Retry sleep interrupted");
        }
    }

    private OrderWorkflowException toWorkflowException(Exception ex, String stepName) {
        if (ex instanceof OrderWorkflowException workflowException) {
            return workflowException;
        }

        if (ex instanceof HttpStatusCodeException statusException) {
            int status = statusException.getStatusCode().value();
            String code = switch (stepName) {
                case "INVENTORY_RESERVE", "INVENTORY_RELEASE" -> "INVENTORY_SERVICE_ERROR";
                case "PAYMENT_PAY", "PAYMENT_REFUND" -> "PAYMENT_SERVICE_ERROR";
                default -> "DOWNSTREAM_ERROR";
            };
            String message = extractErrorMessage(statusException, "Downstream request failed");
            if (status == 409) {
                return new OrderWorkflowException(400, "OUT_OF_STOCK", message);
            }
            return new OrderWorkflowException(502, code, message);
        }

        if (ex instanceof ResourceAccessException) {
            return new OrderWorkflowException(504, "DOWNSTREAM_TIMEOUT", "Downstream request timeout");
        }

        if (ex instanceof IllegalStateException stateException) {
            return new OrderWorkflowException(503, "CIRCUIT_OPEN", stateException.getMessage());
        }

        String message = ex == null ? "Saga step failed" : ex.getMessage();
        return new OrderWorkflowException(502, "SAGA_STEP_FAILED", message);
    }

    private String extractErrorMessage(HttpStatusCodeException exception, String fallback) {
        String responseBody = exception.getResponseBodyAsString();
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return fallback;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errorNode = root.path("error");
            if (errorNode.isObject() && errorNode.has("message")) {
                return errorNode.path("message").asText(fallback);
            }
            if (root.has("message")) {
                return root.path("message").asText(fallback);
            }
        } catch (Exception ignored) {
            // Keep fallback to raw body below.
        }

        return responseBody;
    }

    private HttpHeaders buildServiceHeaders(String correlationId, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-Id", correlationId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }

    private double clampProbability(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static class ProductResponse {
        private Double price;

        public Double getPrice() {
            return price;
        }

        public void setPrice(Double price) {
            this.price = price;
        }
    }
}
