package com.example.payment.service;

import com.example.payment.dto.PayRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.RefundRequest;
import com.example.payment.model.PaymentOperationType;
import com.example.payment.model.PaymentStatus;
import com.example.payment.model.PaymentTransaction;
import com.example.payment.repository.PaymentTransactionRepository;
import com.example.payment.util.StructuredLogger;
import io.opentelemetry.api.trace.Span;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    private static final int MONEY_SCALE = 2;

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
        String currency = normalizeCurrency(request.getCurrency());
        BigDecimal amount = normalizeAmount(request.getAmount());

        Span.current().setAttribute("order_id", request.getOrderId().toString());
        Span.current().setAttribute("saga_step", "PAYMENT_PAY");
        Span.current().setAttribute("compensation", false);

        Optional<PaymentTransaction> existingByIdempotency = repository.findByIdempotencyKey(idempotencyKey);
        if (existingByIdempotency.isPresent()) {
            PaymentTransaction existing = existingByIdempotency.get();
            validatePayReplay(existing, request, amount, currency);
            if (existing.getStatus() == PaymentStatus.FAILED) {
                throw new PaymentException(502, "PAYMENT_DECLINED", "Payment provider rejected transaction");
            }
            return toResponse(existing, true, correlationId);
        }

        maybeInjectChaos("pay", correlationId);
        maybeDelay(paymentDelayMs);

        PaymentTransaction attempt = buildPayTransaction(request, amount, currency, idempotencyKey, correlationId, chance(paymentFailureProbability));
        PersistResult persisted = insertFirstOrReplay(attempt);
        PaymentTransaction transaction = persisted.transaction();

        validatePayReplay(transaction, request, amount, currency);

        if (transaction.getStatus() == PaymentStatus.FAILED) {
            structuredLogger.warn("payment.pay.failed", Map.of(
                    "order_id", request.getOrderId().toString(),
                    "correlation_id", correlationId,
                    "reason", Optional.ofNullable(transaction.getFailureReason()).orElse("provider_rejected")
            ));
            throw new PaymentException(502, "PAYMENT_DECLINED", "Payment provider rejected transaction");
        }

        structuredLogger.info("payment.pay.success", Map.of(
                "payment_id", transaction.getId().toString(),
                "order_id", transaction.getOrderId().toString(),
                "amount", transaction.getAmount(),
                "currency", transaction.getCurrency(),
                "correlation_id", correlationId
        ));

        return toResponse(transaction, persisted.replay(), correlationId);
    }

    @Transactional
    public PaymentResponse refund(RefundRequest request, String idempotencyKeyHeader, String correlationId) {
        correlationId = normalizeCorrelationId(correlationId);
        String idempotencyKey = normalizeIdempotencyKey(idempotencyKeyHeader, request.getIdempotencyKey());
        String currency = normalizeCurrency(request.getCurrency());
        BigDecimal amount = normalizeAmount(request.getAmount());

        Span.current().setAttribute("order_id", request.getOrderId().toString());
        Span.current().setAttribute("saga_step", "PAYMENT_REFUND");
        Span.current().setAttribute("compensation", true);

        Optional<PaymentTransaction> existingByIdempotency = repository.findByIdempotencyKey(idempotencyKey);
        if (existingByIdempotency.isPresent()) {
            PaymentTransaction existing = existingByIdempotency.get();
            validateRefundReplay(existing, request, amount, currency);
            if (existing.getStatus() == PaymentStatus.FAILED) {
                throw new PaymentException(502, "REFUND_DECLINED", "Payment provider rejected refund");
            }
            return toResponse(existing, true, correlationId);
        }

        PaymentTransaction paidTransaction = findReferencePaymentForUpdate(request);
        assertTransitionFromPayToRefund(paidTransaction, request.getOrderId());
        assertCurrencyMatches(currency, paidTransaction.getCurrency());

        BigDecimal refundedSoFar = Optional.ofNullable(
                repository.sumRefundedAmountByReferencePaymentIdExcludingIdempotencyKey(
                        paidTransaction.getId(),
                        idempotencyKey
                )
        ).orElse(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal afterRefund = refundedSoFar.add(amount);
        if (afterRefund.compareTo(paidTransaction.getAmount()) > 0) {
            throw new PaymentException(409, "REFUND_EXCEEDS_CAPTURED_AMOUNT", "Refund amount exceeds captured payment");
        }

        maybeInjectChaos("refund", correlationId);
        maybeDelay(Math.max(100, paymentDelayMs / 2));

        PaymentTransaction attempt = buildRefundTransaction(
                request,
                paidTransaction,
                amount,
                currency,
                idempotencyKey,
                correlationId,
                chance(paymentFailureProbability)
        );
        PersistResult persisted = insertFirstOrReplay(attempt);
        PaymentTransaction transaction = persisted.transaction();

        validateRefundReplay(transaction, request, paidTransaction, amount, currency);

        if (transaction.getStatus() == PaymentStatus.FAILED) {
            structuredLogger.warn("payment.refund.failed", Map.of(
                    "payment_id", paidTransaction.getId().toString(),
                    "order_id", request.getOrderId().toString(),
                    "correlation_id", correlationId,
                    "reason", Optional.ofNullable(transaction.getFailureReason()).orElse("provider_rejected")
            ));
            throw new PaymentException(502, "REFUND_DECLINED", "Payment provider rejected refund");
        }

        structuredLogger.info("payment.refund.success", Map.of(
                "payment_id", transaction.getId().toString(),
                "order_id", transaction.getOrderId().toString(),
                "amount", transaction.getAmount(),
                "correlation_id", correlationId
        ));

        return toResponse(transaction, persisted.replay(), correlationId);
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

    private PaymentTransaction buildPayTransaction(
            PayRequest request,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String correlationId,
            boolean providerRejected
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(request.getOrderId());
        transaction.setUserId(request.getUserId());
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setCorrelationId(correlationId);

        if (providerRejected) {
            transitionToFailed(transaction, PaymentOperationType.PAY, "Provider rejected transaction");
        } else {
            transitionToPaid(transaction);
            transaction.setProviderRef(newProviderRef("pay"));
        }

        return transaction;
    }

    private PaymentTransaction buildRefundTransaction(
            RefundRequest request,
            PaymentTransaction paidTransaction,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String correlationId,
            boolean providerRejected
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(request.getOrderId());
        transaction.setUserId(paidTransaction.getUserId());
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        transaction.setReferencePaymentId(paidTransaction.getId());
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setCorrelationId(correlationId);

        if (providerRejected) {
            transitionToFailed(transaction, PaymentOperationType.REFUND, "Provider rejected refund");
        } else {
            transitionToRefunded(transaction);
            transaction.setProviderRef(newProviderRef("refund"));
        }

        return transaction;
    }

    private PaymentTransaction findReferencePaymentForUpdate(RefundRequest request) {
        if (request.getPaymentId() != null) {
            return repository.findByIdForUpdate(request.getPaymentId())
                    .orElseThrow(() -> new PaymentException(404, "PAYMENT_NOT_FOUND", "Paid transaction not found"));
        }

        return repository.findLatestPaidByOrderIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new PaymentException(404, "PAYMENT_NOT_FOUND", "Paid transaction not found"));
    }

    private PersistResult insertFirstOrReplay(PaymentTransaction transaction) {
        try {
            return new PersistResult(repository.saveAndFlush(transaction), false);
        } catch (DataIntegrityViolationException ex) {
            return repository.findByIdempotencyKey(transaction.getIdempotencyKey())
                    .map(existing -> new PersistResult(existing, true))
                    .orElseThrow(() -> ex);
        }
    }

    private void transitionToPaid(PaymentTransaction transaction) {
        transaction.setOperationType(PaymentOperationType.PAY);
        transaction.setStatus(PaymentStatus.PAID);
        transaction.setFailureReason(null);
    }

    private void transitionToRefunded(PaymentTransaction transaction) {
        transaction.setOperationType(PaymentOperationType.REFUND);
        transaction.setStatus(PaymentStatus.REFUNDED);
        transaction.setFailureReason(null);
    }

    private void transitionToFailed(PaymentTransaction transaction, PaymentOperationType operationType, String reason) {
        transaction.setOperationType(operationType);
        transaction.setStatus(PaymentStatus.FAILED);
        transaction.setFailureReason(reason);
    }

    private void assertTransitionFromPayToRefund(PaymentTransaction payment, UUID orderId) {
        if (!Objects.equals(payment.getOrderId(), orderId)) {
            throw new PaymentException(409, "PAYMENT_ORDER_MISMATCH", "Payment does not belong to this order");
        }
        if (payment.getOperationType() != PaymentOperationType.PAY || payment.getStatus() != PaymentStatus.PAID) {
            throw new PaymentException(409, "INVALID_PAYMENT_STATE", "Only PAID transactions can be refunded");
        }
    }

    private void assertCurrencyMatches(String requestedCurrency, String paidCurrency) {
        if (!requestedCurrency.equalsIgnoreCase(paidCurrency)) {
            throw new PaymentException(409, "REFUND_CURRENCY_MISMATCH", "Refund currency does not match payment currency");
        }
    }

    private void validatePayReplay(PaymentTransaction existing, PayRequest request, BigDecimal amount, String currency) {
        if (existing.getOperationType() != PaymentOperationType.PAY) {
            throw new PaymentException(409, "IDEMPOTENCY_KEY_REUSED", "Idempotency key belongs to a different operation");
        }
        if (!Objects.equals(existing.getOrderId(), request.getOrderId())
                || !Objects.equals(existing.getUserId(), request.getUserId())
                || existing.getAmount().compareTo(amount) != 0
                || !currency.equalsIgnoreCase(existing.getCurrency())) {
            throw new PaymentException(409, "IDEMPOTENCY_KEY_REUSED", "Idempotency key reused with different payload");
        }
    }

    private void validateRefundReplay(
            PaymentTransaction existing,
            RefundRequest request,
            BigDecimal amount,
            String currency
    ) {
        if (existing.getOperationType() != PaymentOperationType.REFUND) {
            throw new PaymentException(409, "IDEMPOTENCY_KEY_REUSED", "Idempotency key belongs to a different operation");
        }
        if (!Objects.equals(existing.getOrderId(), request.getOrderId())
                || existing.getAmount().compareTo(amount) != 0
                || !currency.equalsIgnoreCase(existing.getCurrency())) {
            throw new PaymentException(409, "IDEMPOTENCY_KEY_REUSED", "Idempotency key reused with different payload");
        }
        if (request.getPaymentId() != null
                && !Objects.equals(request.getPaymentId(), existing.getReferencePaymentId())) {
            throw new PaymentException(409, "IDEMPOTENCY_KEY_REUSED", "Idempotency key reused with different payload");
        }
    }

    private void validateRefundReplay(
            PaymentTransaction existing,
            RefundRequest request,
            PaymentTransaction referencePayment,
            BigDecimal amount,
            String currency
    ) {
        if (existing.getOperationType() != PaymentOperationType.REFUND) {
            throw new PaymentException(409, "IDEMPOTENCY_KEY_REUSED", "Idempotency key belongs to a different operation");
        }
        if (!Objects.equals(existing.getOrderId(), request.getOrderId())
                || !Objects.equals(existing.getReferencePaymentId(), referencePayment.getId())
                || existing.getAmount().compareTo(amount) != 0
                || !currency.equalsIgnoreCase(existing.getCurrency())) {
            throw new PaymentException(409, "IDEMPOTENCY_KEY_REUSED", "Idempotency key reused with different payload");
        }
    }

    private PaymentResponse toResponse(PaymentTransaction tx, boolean idempotentReplay, String correlationId) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(tx.getId());
        response.setOrderId(tx.getOrderId());
        response.setStatus(tx.getStatus().name());
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

    private String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new PaymentException(400, "INVALID_CURRENCY", "currency is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new PaymentException(400, "INVALID_CURRENCY", "Unsupported currency code");
        }
        return normalized;
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null) {
            throw new PaymentException(400, "INVALID_AMOUNT", "amount is required");
        }
        BigDecimal normalized = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException(400, "INVALID_AMOUNT", "amount must be greater than 0");
        }
        return normalized;
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

    private record PersistResult(PaymentTransaction transaction, boolean replay) {
    }
}
