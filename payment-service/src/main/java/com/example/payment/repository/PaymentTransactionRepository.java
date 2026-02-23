package com.example.payment.repository;

import com.example.payment.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Optional<PaymentTransaction> findFirstByOrderIdAndOperationTypeAndStatusOrderByCreatedAtDesc(
            UUID orderId,
            String operationType,
            String status
    );
}
