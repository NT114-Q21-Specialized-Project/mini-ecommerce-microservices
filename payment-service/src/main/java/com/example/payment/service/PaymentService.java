package com.example.payment.service;

import com.example.payment.dto.PayRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.RefundRequest;
import com.example.payment.model.PaymentTransaction;
import com.example.payment.repository.PaymentTransactionRepository;
import com.example.payment.util.StructuredLogger;
import io.opentelemetry.api.trace.Span;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    private final PaymentTransactionRepository repository;
    private final StructuredLogger structuredLogger;

    private final double paymentFailureProbability;
    private final int paymentDelayMs;
    private final boolean chaosMode;
    private final double latencyProbability;
    private final double errorProbability;
    private final int chaosDelayMs;

    public PaymentService(
            PaymentTransactionRepository repository,
            StructuredLogger structuredLogger,
            @Value("${payment.failure.probability:0.0}") double paymentFailureProbability,
            @Value("${payment.delay.ms:200}") int paymentDelayMs,
            @Value("${chaos.mode:false}") boolean chaosMode,
            @Value("${chaos.latency.probability:0.0}") double latencyProbability,
            @Value("${chaos.error.probability:0.0}") double errorProbability,
            @Value("${chaos.delay.ms:0}") int chaosDelayMs
    ) {
        this.repository = repository;
        this.structuredLogger = structuredLogger;
        this.paymentFailureProbability = clamp(paymentFailureProbability);
        this.paymentDelayMs = Math.max(paymentDelayMs, 0);
        this.chaosMode = chaosMode;
        this.latencyProbability = clamp(latencyProbability);
        this.errorProbability = clamp(errorProbability);
        this.chaosDelayMs = Math.max(chaosDelayMs, 0);
    }

    @Transactional
    public PaymentResponse pay(PayRequest request, String idempotencyKeyHeader, String correlationId) {
        correlationId = normalizeCorrelationId(correlationId);
        String idempotencyKey = normalizeIdempotencyKey(idempotencyKeyHeader, request.getIdempotencyKey());
        Span.current().setAttribute("order_id", request.getOrderId().toString());
        Span.current().setAttribute("saga_step", "PAYMENT_PAY");
        Span.current().setAttribute("compensation", false);

        Optional<PaymentTransaction> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get(), true, correlationId);
        }

        maybeInjectChaos("pay", correlationId);
        maybeDelay(paymentDelayMs);

        if (chance(paymentFailureProbability)) {
            PaymentTransaction failed = buildBaseTransaction(request, idempotencyKey, correlationId);
            failed.setOperationType("PAY");
            failed.setStatus("FAILED");
            failed.setFailureReason("Provider rejected transaction");
            repository.save(failed);

            structuredLogger.warn("payment.pay.failed", Map.of(
                    "order_id", request.getOrderId().toString(),
                    "correlation_id", correlationId,
                    "reason", "provider_rejected"
            ));
            throw new PaymentException(502, "PAYMENT_DECLINED", "Payment provider rejected transaction");
        }

        PaymentTransaction payment = buildBaseTransaction(request, idempotencyKey, correlationId);
        payment.setOperationType("PAY");
        payment.setStatus("PAID");
        payment.setProviderRef(newProviderRef("pay"));

        PaymentTransaction saved = repository.save(payment);

        structuredLogger.info("payment.pay.success", Map.of(
                "payment_id", saved.getId().toString(),
                "order_id", saved.getOrderId().toString(),
                "amount", saved.getAmount(),
                "currency", saved.getCurrency(),
                "correlation_id", correlationId
        ));

        return toResponse(saved, false, correlationId);
    }

    @Transactional
    public PaymentResponse refund(RefundRequest request, String idempotencyKeyHeader, String correlationId) {
        correlationId = normalizeCorrelationId(correlationId);
        String idempotencyKey = normalizeIdempotencyKey(idempotencyKeyHeader, request.getIdempotencyKey());
        Span.current().setAttribute("order_id", request.getOrderId().toString());
        Span.current().setAttribute("saga_step", "PAYMENT_REFUND");
        Span.current().setAttribute("compensation", true);

        Optional<PaymentTransaction> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get(), true, correlationId);
        }

        PaymentTransaction paidTransaction = findReferencePayment(request);

        maybeInjectChaos("refund", correlationId);
        maybeDelay(Math.max(100, paymentDelayMs / 2));

        PaymentTransaction refund = new PaymentTransaction();
        refund.setOrderId(request.getOrderId());
        refund.setUserId(paidTransaction.getUserId());
        refund.setAmount(request.getAmount());
        refund.setCurrency(request.getCurrency().toUpperCase());
        refund.setOperationType("REFUND");
        refund.setStatus("REFUNDED");
        refund.setProviderRef(newProviderRef("refund"));
        refund.setIdempotencyKey(idempotencyKey);
        refund.setCorrelationId(correlationId);

        PaymentTransaction saved = repository.save(refund);

        structuredLogger.info("payment.refund.success", Map.of(
                "payment_id", saved.getId().toString(),
                "order_id", saved.getOrderId().toString(),
                "amount", saved.getAmount(),
                "correlation_id", correlationId
        ));

        return toResponse(saved, false, correlationId);
    }

    public Object simulateLoad(String type, int amount) {
        if ("memory".equalsIgnoreCase(type)) {
            int mb = Math.min(Math.max(amount, 32), 512);
            byte[] bytes = new byte[mb * 1024 * 1024];
            for (int i = 0; i < bytes.length; i += 4096) {
                bytes[i] = (byte) (i % 127);
            }
            return Map.of("status", "ok", "mode", "memory", "mb", mb);
        }

        int seconds = Math.min(Math.max(amount, 1), 20);
        long endAt = System.currentTimeMillis() + (seconds * 1000L);
        while (System.currentTimeMillis() < endAt) {
            Math.log(ThreadLocalRandom.current().nextDouble(1.0, 1000.0));
        }
        return Map.of("status", "ok", "mode", "cpu", "seconds", seconds);
    }

    public List<PaymentTransaction> getTransactionsByOrder(UUID orderId) {
        return repository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    private PaymentTransaction buildBaseTransaction(PayRequest request, String idempotencyKey, String correlationId) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(request.getOrderId());
        transaction.setUserId(request.getUserId());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency().toUpperCase());
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setCorrelationId(correlationId);
        return transaction;
    }

    private PaymentTransaction findReferencePayment(RefundRequest request) {
        if (request.getPaymentId() != null) {
            return repository.findById(request.getPaymentId())
                    .filter(row -> "PAY".equalsIgnoreCase(row.getOperationType()))
                    .filter(row -> "PAID".equalsIgnoreCase(row.getStatus()))
                    .orElseThrow(() -> new PaymentException(404, "PAYMENT_NOT_FOUND", "Paid transaction not found"));
        }

        return repository.findFirstByOrderIdAndOperationTypeAndStatusOrderByCreatedAtDesc(
                        request.getOrderId(),
                        "PAY",
                        "PAID"
                )
                .orElseThrow(() -> new PaymentException(404, "PAYMENT_NOT_FOUND", "Paid transaction not found"));
    }

    private PaymentResponse toResponse(PaymentTransaction tx, boolean idempotentReplay, String correlationId) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(tx.getId());
        response.setOrderId(tx.getOrderId());
        response.setStatus(tx.getStatus());
        response.setProviderRef(tx.getProviderRef());
        response.setIdempotentReplay(idempotentReplay);
        response.setProcessedAt(Optional.ofNullable(tx.getUpdatedAt()).orElse(Instant.now()));
        response.setCorrelationId(correlationId);
        return response;
    }

    private String normalizeIdempotencyKey(String headerValue, String bodyValue) {
        String key = headerValue;
        if (key == null || key.isBlank()) {
            key = bodyValue;
        }
        if (key == null || key.isBlank()) {
            throw new PaymentException(400, "MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required");
        }
        String normalized = key.trim();
        if (normalized.length() > 128) {
            throw new PaymentException(400, "INVALID_IDEMPOTENCY_KEY", "Idempotency key exceeds 128 characters");
        }
        return normalized;
    }

    private String normalizeCorrelationId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value.trim();
    }

    private void maybeInjectChaos(String stage, String correlationId) {
        if (!chaosMode) {
            return;
        }

        if (chance(latencyProbability) && chaosDelayMs > 0) {
            maybeDelay(chaosDelayMs);
        }

        if (chance(errorProbability)) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("stage", stage);
            fields.put("correlation_id", correlationId);
            structuredLogger.warn("payment.chaos.failure", fields);
            throw new PaymentException(500, "CHAOS_FAILURE", "Injected failure by chaos mode");
        }
    }

    private void maybeDelay(int delayMs) {
        if (delayMs <= 0) {
            return;
        }

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentException(500, "INTERRUPTED", "Payment processing interrupted");
        }
    }

    private boolean chance(double probability) {
        return ThreadLocalRandom.current().nextDouble(0.0, 1.0) < clamp(probability);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String newProviderRef(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
