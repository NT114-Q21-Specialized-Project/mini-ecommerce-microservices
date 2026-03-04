package com.example.order;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.model.Order;
import com.example.order.model.OrderStatus;
import com.example.order.model.OutboxEvent;
import com.example.order.model.OutboxStatus;
import com.example.order.model.SagaStep;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.OutboxEventRepository;
import com.example.order.repository.SagaStepRepository;
import com.example.order.service.OrderCreationResult;
import com.example.order.service.OrderEventPublisher;
import com.example.order.service.OrderService;
import com.example.order.service.OrderWorkflowException;
import com.example.order.service.OutboxPublisherWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@SpringBootTest
@ActiveProfiles("test")
class OrderSagaIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SagaStepRepository sagaStepRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisherWorker outboxPublisherWorker;

    @MockBean
    private OrderEventPublisher orderEventPublisher;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setup() {
        mockServer = MockRestServiceServer.bindTo(restTemplate)
                .ignoreExpectOrder(true)
                .build();

        outboxEventRepository.deleteAll();
        sagaStepRepository.deleteAll();
        orderRepository.deleteAll();

        when(orderEventPublisher.publish(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void sagaSuccessAndOutboxRetryPublishing() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        expectProduct(productId, 15.5);
        expectInventoryReserveOk();
        expectPaymentPayOk();

        OrderCreationResult result = orderService.createOrder(
                userId,
                "USER",
                "idem-success",
                "corr-success",
                createOrderRequest(productId, 2)
        );

        assertThat(result.isIdempotentReplay()).isFalse();
        assertThat(result.getOrder().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        OutboxEvent outboxEvent = singleOutboxEvent();
        assertThat(outboxEvent.getEventType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);

        when(orderEventPublisher.publish(anyString(), anyString(), anyString())).thenReturn(false);
        outboxPublisherWorker.publishAvailableEvents();

        OutboxEvent failedEvent = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertThat(failedEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failedEvent.getRetryCount()).isEqualTo(1);

        failedEvent.setNextAttemptAt(Instant.now().minusSeconds(1));
        outboxEventRepository.save(failedEvent);

        when(orderEventPublisher.publish(anyString(), anyString(), anyString())).thenReturn(true);
        outboxPublisherWorker.publishAvailableEvents();

        OutboxEvent publishedEvent = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertThat(publishedEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(publishedEvent.getPublishedAt()).isNotNull();

        verify(orderEventPublisher, times(2)).publish(Mockito.eq("orders.events"), anyString(), Mockito.eq("corr-success"));
        mockServer.verify();
    }

    @Test
    void sagaFailureMarksOrderFailedAndEnqueuesFailureEvent() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        expectProduct(productId, 10.0);
        mockServer.expect(requestTo("http://inventory-service:8080/inventory/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"out of stock\"}}"));

        assertThatThrownBy(() -> orderService.createOrder(
                userId,
                "USER",
                "idem-failure",
                "corr-failure",
                createOrderRequest(productId, 1)
        ))
                .isInstanceOf(OrderWorkflowException.class)
                .satisfies(ex -> {
                    OrderWorkflowException workflowException = (OrderWorkflowException) ex;
                    assertThat(workflowException.getStatus()).isEqualTo(400);
                    assertThat(workflowException.getCode()).isEqualTo("OUT_OF_STOCK");
                });

        Order failedOrder = singleOrder();
        assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);

        OutboxEvent failedEvent = singleOutboxEvent();
        assertThat(failedEvent.getEventType()).isEqualTo("ORDER_FAILED");
        assertThat(failedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);

        List<SagaStep> steps = sagaStepRepository.findByOrderIdOrderByCreatedAtAsc(failedOrder.getId());
        assertThat(steps).extracting(SagaStep::getStepName)
                .contains("ORDER_CREATED", "INVENTORY_RESERVE");
        assertThat(steps.stream().anyMatch(step ->
                "INVENTORY_RESERVE".equals(step.getStepName()) && "FAILED".equals(step.getStepStatus())))
                .isTrue();

        mockServer.verify();
    }

    @Test
    void compensationRunsWhenPaymentFailsAfterInventoryReserve() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        expectProduct(productId, 22.0);
        expectInventoryReserveOk();
        mockServer.expect(requestTo("http://payment-service:8080/payments/pay"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("{\"message\":\"payment unavailable\"}"));
        expectInventoryReleaseOk();

        assertThatThrownBy(() -> orderService.createOrder(
                userId,
                "USER",
                "idem-compensation",
                "corr-compensation",
                createOrderRequest(productId, 1)
        )).isInstanceOf(OrderWorkflowException.class);

        Order failedOrder = singleOrder();
        assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);

        List<SagaStep> steps = sagaStepRepository.findByOrderIdOrderByCreatedAtAsc(failedOrder.getId());
        assertThat(steps.stream().anyMatch(step ->
                "INVENTORY_RELEASE".equals(step.getStepName()) && step.isCompensation() && "SUCCESS".equals(step.getStepStatus())))
                .isTrue();

        mockServer.verify();
    }

    @Test
    void cancelFlowTriggersCompensationAndCancellationEvent() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        expectProduct(productId, 30.0);
        expectInventoryReserveOk();
        expectPaymentPayOk();
        expectPaymentRefundOk();
        expectInventoryReleaseOk();

        OrderCreationResult created = orderService.createOrder(
                userId,
                "USER",
                "idem-cancel",
                "corr-cancel-create",
                createOrderRequest(productId, 1)
        );
        assertThat(created.getOrder().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        Order cancelled = orderService.cancelOrder(
                created.getOrder().getId(),
                userId,
                "USER",
                "corr-cancel"
        );

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).extracting(OutboxEvent::getEventType)
                .contains("ORDER_CONFIRMED", "ORDER_CANCELLED");

        OutboxEvent cancelEvent = events.stream()
                .filter(event -> "ORDER_CANCELLED".equals(event.getEventType()))
                .max(Comparator.comparing(OutboxEvent::getCreatedAt))
                .orElseThrow();
        assertThat(cancelEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);

        List<SagaStep> steps = sagaStepRepository.findByOrderIdOrderByCreatedAtAsc(cancelled.getId());
        assertThat(steps.stream().anyMatch(step ->
                "ORDER_CANCELLED".equals(step.getStepName()) && "SUCCESS".equals(step.getStepStatus())))
                .isTrue();

        mockServer.verify();
    }

    private CreateOrderRequest createOrderRequest(UUID productId, int quantity) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);
        return request;
    }

    private void expectProduct(UUID productId, double price) {
        mockServer.expect(requestTo("http://product-service:8080/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"price\":" + price + "}", MediaType.APPLICATION_JSON));
    }

    private void expectInventoryReserveOk() {
        mockServer.expect(requestTo("http://inventory-service:8080/inventory/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    }

    private void expectInventoryReleaseOk() {
        mockServer.expect(requestTo("http://inventory-service:8080/inventory/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    }

    private void expectPaymentPayOk() {
        mockServer.expect(requestTo("http://payment-service:8080/payments/pay"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    }

    private void expectPaymentRefundOk() {
        mockServer.expect(requestTo("http://payment-service:8080/payments/refund"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    }

    private Order singleOrder() {
        List<Order> orders = orderRepository.findAll();
        assertThat(orders).hasSize(1);
        return orders.get(0);
    }

    private OutboxEvent singleOutboxEvent() {
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);
        return events.get(0);
    }
}
