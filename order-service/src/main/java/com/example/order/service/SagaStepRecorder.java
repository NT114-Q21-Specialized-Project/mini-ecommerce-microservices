package com.example.order.service;

import com.example.order.dto.OrderSagaStepView;
import com.example.order.model.SagaStep;
import com.example.order.repository.SagaStepRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class SagaStepRecorder {

    private final SagaStepRepository sagaStepRepository;

    public SagaStepRecorder(SagaStepRepository sagaStepRepository) {
        this.sagaStepRepository = sagaStepRepository;
    }

    public void record(
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

    public List<OrderSagaStepView> toViews(UUID orderId) {
        List<SagaStep> steps = sagaStepRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        List<OrderSagaStepView> response = new ArrayList<>(steps.size());
        for (SagaStep step : steps) {
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
            response.add(view);
        }
        return response;
    }
}
