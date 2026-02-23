package com.example.order.repository;

import com.example.order.model.SagaStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SagaStepRepository extends JpaRepository<SagaStep, UUID> {
    List<SagaStep> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
