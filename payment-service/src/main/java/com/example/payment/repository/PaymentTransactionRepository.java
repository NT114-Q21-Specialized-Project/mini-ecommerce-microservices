package com.example.payment.repository;

import com.example.payment.model.PaymentOperationType;
import com.example.payment.model.PaymentStatus;
import com.example.payment.model.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentTransaction p WHERE p.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM PaymentTransaction p
            WHERE p.orderId = :orderId
              AND p.operationType = :operationType
              AND p.status = :status
            ORDER BY p.createdAt DESC
            """)
    List<PaymentTransaction> findByOrderIdAndOperationTypeAndStatusForUpdate(
            @Param("orderId") UUID orderId,
            @Param("operationType") PaymentOperationType operationType,
            @Param("status") PaymentStatus status,
            Pageable pageable
    );

    default Optional<PaymentTransaction> findLatestPaidByOrderIdForUpdate(UUID orderId) {
        return findByOrderIdAndOperationTypeAndStatusForUpdate(
                orderId,
                PaymentOperationType.PAY,
                PaymentStatus.PAID,
                PageRequest.of(0, 1)
        ).stream().findFirst();
    }

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM PaymentTransaction p
            WHERE p.referencePaymentId = :paymentId
              AND p.operationType = com.example.payment.model.PaymentOperationType.REFUND
              AND p.status = com.example.payment.model.PaymentStatus.REFUNDED
              AND p.idempotencyKey <> :idempotencyKey
            """)
    BigDecimal sumRefundedAmountByReferencePaymentIdExcludingIdempotencyKey(
            @Param("paymentId") UUID paymentId,
            @Param("idempotencyKey") String idempotencyKey
    );
}
