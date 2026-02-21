package com.example.order.service;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.PendingOutboxEventView;
import com.example.order.model.Order;
import com.example.order.model.OutboxEvent;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RestTemplate restTemplate;
    private final String productServiceBaseUrl;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${clients.product-service.base-url}") String productServiceBaseUrl
    ) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.productServiceBaseUrl = productServiceBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public OrderCreationResult createOrder(
            UUID authenticatedUserId,
            String authenticatedRole,
            String idempotencyKey,
            CreateOrderRequest request
    ) {
        validateAuthentication(authenticatedUserId, authenticatedRole);
        validateCreatePayload(request, idempotencyKey);
        String normalizedIdempotencyKey = idempotencyKey.trim();

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

            return new OrderCreationResult(existingOrder, true);
        }

        ProductResponse product = getProduct(request.getProductId());
        if (product.getStock() == null || product.getStock() < request.getQuantity()) {
            throw new IllegalArgumentException("Not enough stock");
        }
        if (product.getPrice() == null || product.getPrice() <= 0) {
            throw new IllegalStateException("Invalid product price from product-service");
        }

        decreaseProductStock(request.getProductId(), request.getQuantity());

        Order order = new Order();
        order.setUserId(authenticatedUserId);
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(product.getPrice());
        order.setTotalAmount(product.getPrice() * request.getQuantity());
        order.setStatus("CREATED");
        order.setIdempotencyKey(normalizedIdempotencyKey);

        Order createdOrder = orderRepository.save(order);
        persistOutboxEvent("ORDER_CREATED", createdOrder, authenticatedUserId);

        return new OrderCreationResult(createdOrder, false);
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

    @Transactional
    public Order cancelOrder(UUID orderId, UUID authenticatedUserId, String authenticatedRole) {
        validateAuthentication(authenticatedUserId, authenticatedRole);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(authenticatedRole);
        if (!isAdmin && !order.getUserId().equals(authenticatedUserId)) {
            throw new SecurityException("You can only cancel your own orders");
        }

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            return order;
        }

        if (!"CREATED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Only CREATED orders can be cancelled");
        }

        increaseProductStock(order.getProductId(), order.getQuantity());
        order.setStatus("CANCELLED");
        order.setCancelledAt(Instant.now());

        Order cancelledOrder = orderRepository.save(order);
        persistOutboxEvent("ORDER_CANCELLED", cancelledOrder, authenticatedUserId);
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
            throw new IllegalArgumentException("Request body is required");
        }

        if (request.getProductId() == null) {
            throw new IllegalArgumentException("productId is required");
        }

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        if (idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key is too long (max 128)");
        }
    }

    private ProductResponse getProduct(UUID productId) {
        String productUrl = productServiceBaseUrl + "/products/" + productId;

        try {
            ProductResponse response = restTemplate.getForObject(productUrl, ProductResponse.class);
            if (response == null) {
                throw new IllegalStateException("Invalid response from product-service");
            }
            return response;
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Product not found");
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Product-service returned error: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Product service unavailable at " + productUrl);
        } catch (RestClientException e) {
            throw new IllegalStateException("Error communicating with product-service: " + e.getMessage());
        }
    }

    private void decreaseProductStock(UUID productId, Integer quantity) {
        String decreaseStockUrl = productServiceBaseUrl + "/products/" + productId
                + "/decrease-stock?quantity=" + quantity;
        callProductStockEndpoint(decreaseStockUrl);
    }

    private void increaseProductStock(UUID productId, Integer quantity) {
        String increaseStockUrl = productServiceBaseUrl + "/products/" + productId
                + "/increase-stock?quantity=" + quantity;
        callProductStockEndpoint(increaseStockUrl);
    }

    private void callProductStockEndpoint(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Internal-Caller", "order-service");
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, requestEntity, Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Product not found");
        } catch (HttpClientErrorException.BadRequest e) {
            String message = extractErrorMessage(e, "Unable to update product stock");
            throw new IllegalArgumentException(message);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException("Product internal endpoint rejected the request");
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Product-service returned error: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Product service unavailable at " + url);
        } catch (RestClientException e) {
            throw new IllegalStateException("Error communicating with product-service: " + e.getMessage());
        }
    }

    private void persistOutboxEvent(String eventType, Order order, UUID actorUserId) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("ORDER");
        outboxEvent.setAggregateId(order.getId());
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(buildOutboxPayload(eventType, order, actorUserId));
        outboxEvent.setStatus("PENDING");

        outboxEventRepository.save(outboxEvent);
    }

    private String buildOutboxPayload(String eventType, Order order, UUID actorUserId) {
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

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize outbox payload", e);
        }
    }

    private String extractErrorMessage(HttpClientErrorException exception, String fallback) {
        String responseBody = exception.getResponseBodyAsString();
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return fallback;
        }
        return responseBody;
    }

    public static class OrderCreationResult {
        private final Order order;
        private final boolean idempotentReplay;

        public OrderCreationResult(Order order, boolean idempotentReplay) {
            this.order = order;
            this.idempotentReplay = idempotentReplay;
        }

        public Order getOrder() {
            return order;
        }

        public boolean isIdempotentReplay() {
            return idempotentReplay;
        }
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }

    private static class ProductResponse {
        private Double price;
        private Integer stock;

        public Double getPrice() {
            return price;
        }

        public void setPrice(Double price) {
            this.price = price;
        }

        public Integer getStock() {
            return stock;
        }

        public void setStock(Integer stock) {
            this.stock = stock;
        }
    }
}
