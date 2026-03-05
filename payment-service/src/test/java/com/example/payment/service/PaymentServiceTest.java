package com.example.payment.service;

import com.example.payment.dto.PayRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.RefundRequest;
import com.example.payment.model.PaymentOperationType;
import com.example.payment.model.PaymentStatus;
import com.example.payment.model.PaymentTransaction;
import com.example.payment.repository.PaymentTransactionRepository;
import com.example.payment.util.StructuredLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private PaymentTransactionRepository repository;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(PaymentTransactionRepository.class);
        service = new PaymentService(
                repository,
                new StructuredLogger(new ObjectMapper()),
                0.0,
                0,
                false,
                0.0,
                0.0,
                0
        );
    }

    @Test
    void payShouldReplayDuplicateRequestOnUniqueViolation() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "pay-duplicate-key";

        PayRequest request = new PayRequest();
        request.setOrderId(orderId);
        request.setUserId(userId);
        request.setAmount(new BigDecimal("199.95"));
        request.setCurrency("USD");

        PaymentTransaction existing = buildTransaction(
                orderId,
                userId,
                new BigDecimal("199.95"),
                "USD",
                PaymentOperationType.PAY,
                PaymentStatus.PAID,
                idempotencyKey
        );

        when(repository.saveAndFlush(any(PaymentTransaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));
        when(repository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        PaymentResponse response = service.pay(request, idempotencyKey, "corr-1");

        assertTrue(response.isIdempotentReplay());
        assertEquals(existing.getId(), response.getPaymentId());
        assertEquals("PAID", response.getStatus());
    }

    @Test
    void payShouldPersistFailedStateAndReplayFailure() {
        service = new PaymentService(
                repository,
                new StructuredLogger(new ObjectMapper()),
                1.0,
                0,
                false,
                0.0,
                0.0,
                0
        );

        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "pay-failed-key";
        AtomicReference<PaymentTransaction> firstPersisted = new AtomicReference<>();

        PayRequest request = new PayRequest();
        request.setOrderId(orderId);
        request.setUserId(userId);
        request.setAmount(new BigDecimal("49.99"));
        request.setCurrency("USD");
        request.setIdempotencyKey(idempotencyKey);

        when(repository.saveAndFlush(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> {
                    PaymentTransaction tx = invocation.getArgument(0);
                    setId(tx, UUID.randomUUID());
                    setTimestamps(tx);
                    firstPersisted.set(tx);
                    return tx;
                })
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        when(repository.findByIdempotencyKey(idempotencyKey))
                .thenAnswer(invocation -> Optional.ofNullable(firstPersisted.get()));

        PaymentException first = assertThrows(
                PaymentException.class,
                () -> service.pay(request, idempotencyKey, "corr-2")
        );
        assertEquals(502, first.getStatus());
        assertEquals("PAYMENT_DECLINED", first.getCode());

        PaymentException replay = assertThrows(
                PaymentException.class,
                () -> service.pay(request, idempotencyKey, "corr-3")
        );
        assertEquals(502, replay.getStatus());
        assertEquals("PAYMENT_DECLINED", replay.getCode());

        assertEquals(PaymentOperationType.PAY, firstPersisted.get().getOperationType());
        assertEquals(PaymentStatus.FAILED, firstPersisted.get().getStatus());
    }

    @Test
    void refundShouldRejectOverRefund() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PaymentTransaction paid = buildTransaction(
                orderId,
                userId,
                new BigDecimal("100.00"),
                "USD",
                PaymentOperationType.PAY,
                PaymentStatus.PAID,
                "pay-key"
        );
        setId(paid, paymentId);

        RefundRequest request = new RefundRequest();
        request.setOrderId(orderId);
        request.setPaymentId(paymentId);
        request.setAmount(new BigDecimal("30.00"));
        request.setCurrency("USD");
        request.setIdempotencyKey("refund-over-limit-key");

        when(repository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(paid));
        when(repository.sumRefundedAmountByReferencePaymentIdExcludingIdempotencyKey(paymentId, "refund-over-limit-key"))
                .thenReturn(new BigDecimal("80.00"));

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> service.refund(request, "refund-over-limit-key", "corr-4")
        );

        assertEquals(409, exception.getStatus());
        assertEquals("REFUND_EXCEEDS_CAPTURED_AMOUNT", exception.getCode());
        verify(repository, never()).saveAndFlush(any(PaymentTransaction.class));
    }

    @Test
    void refundShouldReplayDuplicateRefundOnUniqueViolation() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "refund-duplicate-key";

        PaymentTransaction paid = buildTransaction(
                orderId,
                userId,
                new BigDecimal("200.00"),
                "USD",
                PaymentOperationType.PAY,
                PaymentStatus.PAID,
                "pay-key-2"
        );
        setId(paid, paymentId);

        PaymentTransaction existingRefund = buildTransaction(
                orderId,
                userId,
                new BigDecimal("50.00"),
                "USD",
                PaymentOperationType.REFUND,
                PaymentStatus.REFUNDED,
                idempotencyKey
        );
        existingRefund.setReferencePaymentId(paymentId);

        RefundRequest request = new RefundRequest();
        request.setOrderId(orderId);
        request.setPaymentId(paymentId);
        request.setAmount(new BigDecimal("50.00"));
        request.setCurrency("USD");
        request.setIdempotencyKey(idempotencyKey);

        when(repository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(paid));
        when(repository.sumRefundedAmountByReferencePaymentIdExcludingIdempotencyKey(paymentId, idempotencyKey))
                .thenReturn(BigDecimal.ZERO);
        when(repository.saveAndFlush(any(PaymentTransaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));
        when(repository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingRefund));

        PaymentResponse response = service.refund(request, idempotencyKey, "corr-5");

        assertTrue(response.isIdempotentReplay());
        assertEquals(existingRefund.getId(), response.getPaymentId());
        assertEquals("REFUNDED", response.getStatus());
    }

    private static PaymentTransaction buildTransaction(
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String currency,
            PaymentOperationType operationType,
            PaymentStatus status,
            String idempotencyKey
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        setId(transaction, UUID.randomUUID());
        transaction.setOrderId(orderId);
        transaction.setUserId(userId);
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        transaction.setOperationType(operationType);
        transaction.setStatus(status);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setProviderRef("provider-ref");
        transaction.setCorrelationId("corr-test");
        setTimestamps(transaction);
        return transaction;
    }

    private static void setId(PaymentTransaction tx, UUID id) {
        try {
            var field = PaymentTransaction.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(tx, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot set payment transaction id for test", e);
        }
    }

    private static void setTimestamps(PaymentTransaction tx) {
        try {
            var createdAt = PaymentTransaction.class.getDeclaredField("createdAt");
            createdAt.setAccessible(true);
            createdAt.set(tx, Instant.now());
            var updatedAt = PaymentTransaction.class.getDeclaredField("updatedAt");
            updatedAt.setAccessible(true);
            updatedAt.set(tx, Instant.now());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot set payment transaction timestamps for test", e);
        }
    }
}
