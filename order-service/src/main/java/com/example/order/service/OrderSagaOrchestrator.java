package com.example.order.service;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.model.Order;
import com.example.order.model.OrderEventType;
import com.example.order.model.OrderStatus;
import com.example.order.repository.OrderRepository;
import com.example.order.util.StructuredLogger;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderSagaOrchestrator {

    private final OrderRepository orderRepository;
    private final OrderClientAdapter orderClientAdapter;
    private final OrderCompensationHandler compensationHandler;
    private final OrderStatePolicy orderStatePolicy;
    private final SagaStepRecorder sagaStepRecorder;
    private final OrderOutboxService orderOutboxService;
    private final StructuredLogger structuredLogger;

    public OrderSagaOrchestrator(
            OrderRepository orderRepository,
            OrderClientAdapter orderClientAdapter,
            OrderCompensationHandler compensationHandler,
            OrderStatePolicy orderStatePolicy,
            SagaStepRecorder sagaStepRecorder,
            OrderOutboxService orderOutboxService,
            StructuredLogger structuredLogger
    ) {
        this.orderRepository = orderRepository;
        this.orderClientAdapter = orderClientAdapter;
        this.compensationHandler = compensationHandler;
        this.orderStatePolicy = orderStatePolicy;
        this.sagaStepRecorder = sagaStepRecorder;
        this.orderOutboxService = orderOutboxService;
        this.structuredLogger = structuredLogger;
    }

    @Transactional(dontRollbackOn = OrderWorkflowException.class)
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
            validateIdempotentRequest(existingOrder, authenticatedUserId, request);
            return new OrderCreationResult(
                    existingOrder,
                    true,
                    normalizedCorrelationId,
                    sagaStepRecorder.toViews(existingOrder.getId())
            );
        }

        OrderClientAdapter.ProductResponse product = orderClientAdapter.getProduct(request.getProductId(), normalizedCorrelationId);
        if (product.getPrice() == null || product.getPrice() <= 0) {
            throw new OrderWorkflowException(502, "INVALID_PRODUCT_PRICE", "Invalid product price from product-service");
        }

        Order order = new Order();
        order.setUserId(authenticatedUserId);
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(product.getPrice());
        order.setTotalAmount(product.getPrice() * request.getQuantity());
        order.setIdempotencyKey(normalizedIdempotencyKey);
        orderStatePolicy.initialize(order);

        SaveResult saveResult = saveWithIdempotencyRaceProtection(order, authenticatedUserId, request);
        Order savedOrder = saveResult.order();
        if (saveResult.idempotentReplay()) {
            return new OrderCreationResult(
                    savedOrder,
                    true,
                    normalizedCorrelationId,
                    sagaStepRecorder.toViews(savedOrder.getId())
            );
        }

        sagaStepRecorder.record(savedOrder.getId(), "ORDER_CREATED", "SUCCESS", 0, false,
                "Order initialized", normalizedCorrelationId);

        boolean inventoryReserved = false;
        boolean paymentCaptured = false;

        try {
            orderClientAdapter.reserveInventory(savedOrder, normalizedCorrelationId);
            inventoryReserved = true;

            orderStatePolicy.transition(savedOrder, OrderStatus.INVENTORY_RESERVED, null);
            savedOrder = orderRepository.save(savedOrder);

            sagaStepRecorder.record(savedOrder.getId(), "PAYMENT_PENDING", "SUCCESS", 0, false,
                    "Ready to process payment", normalizedCorrelationId);
            orderStatePolicy.transition(savedOrder, OrderStatus.PAYMENT_PENDING, null);
            savedOrder = orderRepository.save(savedOrder);

            orderClientAdapter.capturePayment(savedOrder, normalizedCorrelationId);
            paymentCaptured = true;

            orderStatePolicy.transition(savedOrder, OrderStatus.CONFIRMED, null);
            savedOrder = orderRepository.save(savedOrder);
            sagaStepRecorder.record(savedOrder.getId(), "ORDER_CONFIRMED", "SUCCESS", 0, false,
                    "Order confirmed", normalizedCorrelationId);
            orderOutboxService.enqueueOrderEvent(OrderEventType.ORDER_CONFIRMED, savedOrder, authenticatedUserId, normalizedCorrelationId);

            structuredLogger.info("order.saga.success", Map.of(
                    "order_id", savedOrder.getId().toString(),
                    "correlation_id", normalizedCorrelationId
            ));

            return new OrderCreationResult(
                    savedOrder,
                    false,
                    normalizedCorrelationId,
                    sagaStepRecorder.toViews(savedOrder.getId())
            );
        } catch (Exception ex) {
            String failureReason = orderClientAdapter.extractFailureReason(ex);

            compensationHandler.compensateAfterCreateFailure(savedOrder, paymentCaptured, inventoryReserved, normalizedCorrelationId);

            orderStatePolicy.transition(savedOrder, OrderStatus.FAILED, failureReason);
            savedOrder = orderRepository.save(savedOrder);
            orderOutboxService.enqueueOrderEvent(OrderEventType.ORDER_FAILED, savedOrder, authenticatedUserId, normalizedCorrelationId);

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

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }

        if (!orderStatePolicy.canCancel(order.getStatus())) {
            if (order.getStatus() == OrderStatus.FAILED) {
                throw new OrderWorkflowException(400, "ORDER_ALREADY_FAILED", "Failed orders cannot be cancelled");
            }
            throw new OrderWorkflowException(409, "ORDER_NOT_CANCELLABLE", "Order cannot be cancelled from state " + order.getStatus());
        }

        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            compensationHandler.compensateBeforeCancel(order, normalizedCorrelationId);
        } else if (order.getStatus() == OrderStatus.INVENTORY_RESERVED) {
            compensationHandler.releaseInventoryBeforeCancel(order, normalizedCorrelationId);
        }

        orderStatePolicy.transition(order, OrderStatus.CANCELLED, null);
        Order cancelledOrder = orderRepository.save(order);

        sagaStepRecorder.record(order.getId(), "ORDER_CANCELLED", "SUCCESS", 0, true,
                "Order cancelled by user", normalizedCorrelationId);
        orderOutboxService.enqueueOrderEvent(OrderEventType.ORDER_CANCELLED, cancelledOrder, authenticatedUserId, normalizedCorrelationId);

        return cancelledOrder;
    }

    private SaveResult saveWithIdempotencyRaceProtection(Order order, UUID authenticatedUserId, CreateOrderRequest request) {
        try {
            return new SaveResult(orderRepository.saveAndFlush(order), false);
        } catch (DataIntegrityViolationException ex) {
            Optional<Order> existingOrderOptional = orderRepository.findByIdempotencyKey(order.getIdempotencyKey());
            if (existingOrderOptional.isEmpty()) {
                throw ex;
            }

            Order existingOrder = existingOrderOptional.get();
            validateIdempotentRequest(existingOrder, authenticatedUserId, request);
            return new SaveResult(existingOrder, true);
        }
    }

    private void validateIdempotentRequest(Order existingOrder, UUID authenticatedUserId, CreateOrderRequest request) {
        if (!existingOrder.getUserId().equals(authenticatedUserId)) {
            throw new SecurityException("Idempotency key belongs to another user");
        }

        if (!existingOrder.getProductId().equals(request.getProductId()) ||
                !existingOrder.getQuantity().equals(request.getQuantity())) {
            throw new IdempotencyConflictException("Idempotency key already used with different payload");
        }
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

    private String normalizeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationId.trim();
    }

    private record SaveResult(Order order, boolean idempotentReplay) {
    }
}
