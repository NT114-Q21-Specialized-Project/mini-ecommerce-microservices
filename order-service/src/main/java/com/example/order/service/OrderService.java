package com.example.order.service;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderSagaStepView;
import com.example.order.dto.PendingOutboxEventView;
import com.example.order.model.Order;
import com.example.order.model.OutboxEvent;
import com.example.order.model.SagaStep;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.OutboxEventRepository;
import com.example.order.repository.SagaStepRepository;
import com.example.order.util.SimpleCircuitBreaker;
import com.example.order.util.StructuredLogger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SagaStepRepository sagaStepRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OrderEventPublisher orderEventPublisher;
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

    public OrderService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            SagaStepRepository sagaStepRepository,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            OrderEventPublisher orderEventPublisher,
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
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.sagaStepRepository = sagaStepRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.orderEventPublisher = orderEventPublisher;
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

    @Transactional
    public OrderCreationResult createOrder(
            UUID authenticatedUserId,
            String authenticatedRole,
            String idempotencyKey,
            String correlationId,
            CreateOrderRequest request
    ) {
        validateAuthentication(authenticatedUserId, authenticatedRole);
        validateCreatePayload(request, idempotencyKey);

        String normalizedIdempotencyKey = idempotencyKey.trim();
        String normalizedCorrelationId = normalizeCorrelationId(correlationId);

        Optional<Order> existingOrderOptional = orderRepository.findByIdempotencyKey(normalizedIdempotencyKey);
        if (existingOrderOptional.isPresent()) {
            Order existingOrder = existingOrderOptional.get();

            if (!existingOrder.getUserId().equals(authenticatedUserId)) {
                throw new SecurityException("Idempotency key belongs to another user");
            }

            if (!existingOrder.getProductId().equals(request.getProductId()) ||
                    !existingOrder.getQuantity().equals(request.getQuantity())) {
                throw new IdempotencyConflictException("Idempotency key already used with different payload");
            }

            return new OrderCreationResult(
                    existingOrder,
                    true,
                    normalizedCorrelationId,
                    listOrderSagaSteps(existingOrder.getId(), authenticatedUserId, authenticatedRole)
            );
        }

        ProductResponse product = getProduct(request.getProductId(), normalizedCorrelationId);
        if (product.getPrice() == null || product.getPrice() <= 0) {
            throw new OrderWorkflowException(502, "INVALID_PRODUCT_PRICE", "Invalid product price from product-service");
        }

        Order order = new Order();
        order.setUserId(authenticatedUserId);
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(product.getPrice());
        order.setTotalAmount(product.getPrice() * request.getQuantity());
        order.setStatus("CREATED");
        order.setIdempotencyKey(normalizedIdempotencyKey);

        Order savedOrder = orderRepository.save(order);
        recordSagaStep(savedOrder.getId(), "ORDER_CREATED", "SUCCESS", 0, false,
                "Order initialized", normalizedCorrelationId);

        boolean inventoryReserved = false;
        boolean paymentCaptured = false;

        try {
            reserveInventory(savedOrder, normalizedCorrelationId);
            inventoryReserved = true;

            updateOrderStatus(savedOrder, "INVENTORY_RESERVED", null);

            recordSagaStep(savedOrder.getId(), "PAYMENT_PENDING", "SUCCESS", 0, false,
                    "Ready to process payment", normalizedCorrelationId);
            updateOrderStatus(savedOrder, "PAYMENT_PENDING", null);

            capturePayment(savedOrder, normalizedCorrelationId);
            paymentCaptured = true;

            updateOrderStatus(savedOrder, "CONFIRMED", null);
            recordSagaStep(savedOrder.getId(), "ORDER_CONFIRMED", "SUCCESS", 0, false,
                    "Order confirmed", normalizedCorrelationId);
            persistOutboxEvent("ORDER_CONFIRMED", savedOrder, authenticatedUserId, normalizedCorrelationId);

            structuredLogger.info("order.saga.success", Map.of(
                    "order_id", savedOrder.getId().toString(),
                    "correlation_id", normalizedCorrelationId
            ));

            return new OrderCreationResult(
                    savedOrder,
                    false,
                    normalizedCorrelationId,
                    listOrderSagaSteps(savedOrder.getId(), authenticatedUserId, authenticatedRole)
            );
        } catch (Exception ex) {
            String failureReason = extractFailureReason(ex);

            if (paymentCaptured) {
                try {
                    refundPayment(savedOrder, normalizedCorrelationId);
                } catch (Exception compensationEx) {
                    recordSagaStep(savedOrder.getId(), "PAYMENT_REFUND", "FAILED", 0, true,
                            extractFailureReason(compensationEx), normalizedCorrelationId);
                }
            }

            if (inventoryReserved) {
                try {
                    releaseInventory(savedOrder, normalizedCorrelationId);
                } catch (Exception compensationEx) {
                    recordSagaStep(savedOrder.getId(), "INVENTORY_RELEASE", "FAILED", 0, true,
                            extractFailureReason(compensationEx), normalizedCorrelationId);
                }
            }

            updateOrderStatus(savedOrder, "FAILED", failureReason);
            persistOutboxEvent("ORDER_FAILED", savedOrder, authenticatedUserId, normalizedCorrelationId);

            structuredLogger.error("order.saga.failed", Map.of(
                    "order_id", savedOrder.getId().toString(),
                    "correlation_id", normalizedCorrelationId,
                    "error", failureReason
            ));

            if (ex instanceof OrderWorkflowException workflowException) {
                throw workflowException;
            }
            throw new OrderWorkflowException(502, "SAGA_FAILED", failureReason);
        }
    }

    public List<Order> getOrders(UUID authenticatedUserId, String authenticatedRole, UUID requestedUserId) {
        validateAuthentication(authenticatedUserId, authenticatedRole);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(authenticatedRole);

        if (isAdmin) {
            if (requestedUserId != null) {
                return orderRepository.findByUserIdOrderByCreatedAtDesc(requestedUserId);
            }
            return orderRepository.findAllByOrderByCreatedAtDesc();
        }

        return orderRepository.findByUserIdOrderByCreatedAtDesc(authenticatedUserId);
    }

    public List<OrderSagaStepView> listOrderSagaSteps(UUID orderId, UUID authenticatedUserId, String authenticatedRole) {
        validateAuthentication(authenticatedUserId, authenticatedRole);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderWorkflowException(404, "ORDER_NOT_FOUND", "Order not found"));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(authenticatedRole);
        if (!isAdmin && !order.getUserId().equals(authenticatedUserId)) {
            throw new SecurityException("You can only view saga steps for your own orders");
        }

        List<SagaStep> steps = sagaStepRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        List<OrderSagaStepView> response = new ArrayList<>();
        for (SagaStep step : steps) {
            response.add(toSagaStepView(step));
        }
        return response;
    }

    @Transactional
    public Order cancelOrder(UUID orderId, UUID authenticatedUserId, String authenticatedRole, String correlationId) {
        validateAuthentication(authenticatedUserId, authenticatedRole);
        String normalizedCorrelationId = normalizeCorrelationId(correlationId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderWorkflowException(404, "ORDER_NOT_FOUND", "Order not found"));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(authenticatedRole);
        if (!isAdmin && !order.getUserId().equals(authenticatedUserId)) {
            throw new SecurityException("You can only cancel your own orders");
        }

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            return order;
        }

        if ("FAILED".equalsIgnoreCase(order.getStatus())) {
            throw new OrderWorkflowException(400, "ORDER_ALREADY_FAILED", "Failed orders cannot be cancelled");
        }

        if ("CONFIRMED".equalsIgnoreCase(order.getStatus()) ||
                "PAYMENT_PENDING".equalsIgnoreCase(order.getStatus())) {
            try {
                refundPayment(order, normalizedCorrelationId);
            } catch (Exception ex) {
                throw new OrderWorkflowException(502, "PAYMENT_REFUND_FAILED", extractFailureReason(ex));
            }
        }

        if ("INVENTORY_RESERVED".equalsIgnoreCase(order.getStatus()) ||
                "CONFIRMED".equalsIgnoreCase(order.getStatus()) ||
                "PAYMENT_PENDING".equalsIgnoreCase(order.getStatus())) {
            try {
                releaseInventory(order, normalizedCorrelationId);
            } catch (Exception ex) {
                throw new OrderWorkflowException(502, "INVENTORY_RELEASE_FAILED", extractFailureReason(ex));
            }
        }

        order.setStatus("CANCELLED");
        order.setCancelledAt(Instant.now());
        Order cancelledOrder = orderRepository.save(order);

        recordSagaStep(order.getId(), "ORDER_CANCELLED", "SUCCESS", 0, false,
                "Order cancelled by user", normalizedCorrelationId);
        persistOutboxEvent("ORDER_CANCELLED", cancelledOrder, authenticatedUserId, normalizedCorrelationId);

        return cancelledOrder;
    }

    public List<PendingOutboxEventView> getPendingOutboxEvents(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<PendingOutboxEventView> events = outboxEventRepository.findPendingViewByStatus("PENDING");
        if (events.size() <= safeLimit) {
            return events;
        }
        return events.subList(0, safeLimit);
    }

    private void reserveInventory(Order order, String correlationId) {
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

    private void releaseInventory(Order order, String correlationId) {
        String endpoint = inventoryServiceBaseUrl + "/inventory/release";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("productId", order.getProductId());
        payload.put("quantity", order.getQuantity());

        executeWithRetry(
                "INVENTORY_RELEASE",
                order,
                correlationId,
                true,
                inventoryCircuitBreaker,
                () -> {
                    HttpHeaders headers = buildServiceHeaders(correlationId, order.getIdempotencyKey() + ":inventory:release");
                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
                    restTemplate.exchange(endpoint, HttpMethod.POST, requestEntity, Map.class);
                    return true;
                }
        );
    }

    private void capturePayment(Order order, String correlationId) {
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

    private void refundPayment(Order order, String correlationId) {
        String endpoint = paymentServiceBaseUrl + "/payments/refund";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("amount", order.getTotalAmount());
        payload.put("currency", "USD");

        executeWithRetry(
                "PAYMENT_REFUND",
                order,
                correlationId,
                true,
                paymentCircuitBreaker,
                () -> {
                    HttpHeaders headers = buildServiceHeaders(correlationId, order.getIdempotencyKey() + ":payment:refund");
                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
                    restTemplate.exchange(endpoint, HttpMethod.POST, requestEntity, Map.class);
                    return true;
                }
        );
    }

    private ProductResponse getProduct(UUID productId, String correlationId) {
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
                recordSagaStep(
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
                recordSagaStep(
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
                // Keep API compatibility with existing clients/tests that expect 400.
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

    private String extractFailureReason(Exception ex) {
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

    private void validateAuthentication(UUID authenticatedUserId, String authenticatedRole) {
        if (authenticatedUserId == null) {
            throw new SecurityException("Missing authenticated user");
        }

        if (authenticatedRole == null || authenticatedRole.isBlank()) {
            throw new SecurityException("Missing authenticated role");
        }
    }

    private void validateCreatePayload(CreateOrderRequest request, String idempotencyKey) {
        if (request == null) {
            throw new OrderWorkflowException(400, "INVALID_REQUEST", "Request body is required");
        }

        if (request.getProductId() == null) {
            throw new OrderWorkflowException(400, "INVALID_REQUEST", "productId is required");
        }

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new OrderWorkflowException(400, "INVALID_REQUEST", "quantity must be greater than 0");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new OrderWorkflowException(400, "MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required");
        }

        if (idempotencyKey.length() > 128) {
            throw new OrderWorkflowException(400, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key is too long (max 128)");
        }
    }

    private void updateOrderStatus(Order order, String status, String failureReason) {
        order.setStatus(status);
        order.setFailureReason(failureReason);
        orderRepository.save(order);
    }

    private void recordSagaStep(
            UUID orderId,
            String stepName,
            String stepStatus,
            int retryCount,
            boolean compensation,
            String detail,
            String correlationId
    ) {
        SagaStep step = new SagaStep();
        step.setOrderId(orderId);
        step.setStepName(stepName);
        step.setStepStatus(stepStatus);
        step.setRetryCount(retryCount);
        step.setCompensation(compensation);
        step.setDetail(detail);
        step.setCorrelationId(correlationId);
        sagaStepRepository.save(step);
    }

    private OrderSagaStepView toSagaStepView(SagaStep step) {
        OrderSagaStepView view = new OrderSagaStepView();
        view.setId(step.getId());
        view.setOrderId(step.getOrderId());
        view.setStepName(step.getStepName());
        view.setStepStatus(step.getStepStatus());
        view.setRetryCount(step.getRetryCount());
        view.setCompensation(step.isCompensation());
        view.setDetail(step.getDetail());
        view.setCorrelationId(step.getCorrelationId());
        view.setCreatedAt(step.getCreatedAt());
        return view;
    }

    private void persistOutboxEvent(String eventType, Order order, UUID actorUserId, String correlationId) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("ORDER");
        outboxEvent.setAggregateId(order.getId());
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(buildOutboxPayload(eventType, order, actorUserId, correlationId));
        outboxEvent.setStatus("PENDING");

        outboxEventRepository.save(outboxEvent);
        orderEventPublisher.publish("orders.events", outboxEvent.getPayload(), correlationId);
    }

    private String buildOutboxPayload(String eventType, Order order, UUID actorUserId, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("occurredAt", Instant.now());
        payload.put("actorUserId", actorUserId);
        payload.put("orderId", order.getId());
        payload.put("userId", order.getUserId());
        payload.put("productId", order.getProductId());
        payload.put("quantity", order.getQuantity());
        payload.put("unitPrice", order.getUnitPrice());
        payload.put("totalAmount", order.getTotalAmount());
        payload.put("status", order.getStatus());
        payload.put("failureReason", order.getFailureReason());
        payload.put("correlationId", correlationId);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize outbox payload", e);
        }
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

    private String normalizeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationId.trim();
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

    public static class OrderCreationResult {
        private final Order order;
        private final boolean idempotentReplay;
        private final String correlationId;
        private final List<OrderSagaStepView> sagaSteps;

        public OrderCreationResult(
                Order order,
                boolean idempotentReplay,
                String correlationId,
                List<OrderSagaStepView> sagaSteps
        ) {
            this.order = order;
            this.idempotentReplay = idempotentReplay;
            this.correlationId = correlationId;
            this.sagaSteps = sagaSteps;
        }

        public Order getOrder() {
            return order;
        }

        public boolean isIdempotentReplay() {
            return idempotentReplay;
        }

        public String getCorrelationId() {
            return correlationId;
        }

        public List<OrderSagaStepView> getSagaSteps() {
            return sagaSteps;
        }
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }

    private static class ProductResponse {
        private Double price;

        public Double getPrice() {
            return price;
        }

        public void setPrice(Double price) {
            this.price = price;
        }
    }
}
