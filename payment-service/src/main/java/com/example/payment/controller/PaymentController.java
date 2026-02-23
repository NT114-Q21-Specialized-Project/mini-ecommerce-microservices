package com.example.payment.controller;

import com.example.payment.config.CorrelationIdFilter;
import com.example.payment.dto.PayRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.RefundRequest;
import com.example.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments/pay")
    public ResponseEntity<PaymentResponse> pay(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = CorrelationIdFilter.CORRELATION_ID_HEADER, required = false) String correlationId,
            @Valid @RequestBody PayRequest request
    ) {
        return ResponseEntity.ok(paymentService.pay(request, idempotencyKey, correlationId));
    }

    @PostMapping("/payments/refund")
    public ResponseEntity<PaymentResponse> refund(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = CorrelationIdFilter.CORRELATION_ID_HEADER, required = false) String correlationId,
            @Valid @RequestBody RefundRequest request
    ) {
        return ResponseEntity.ok(paymentService.refund(request, idempotencyKey, correlationId));
    }

    @GetMapping("/simulate-load")
    public ResponseEntity<Object> simulateLoad(
            @RequestParam(value = "type", defaultValue = "cpu") String type,
            @RequestParam(value = "amount", defaultValue = "2") int amount
    ) {
        return ResponseEntity.ok(paymentService.simulateLoad(type, amount));
    }

    @GetMapping("/simulate-cpu")
    public ResponseEntity<Object> simulateCpu(
            @RequestParam(value = "seconds", defaultValue = "2") int seconds
    ) {
        return ResponseEntity.ok(paymentService.simulateLoad("cpu", seconds));
    }

    @GetMapping("/payments/simulate-cpu")
    public ResponseEntity<Object> simulateCpuViaGateway(
            @RequestParam(value = "seconds", defaultValue = "2") int seconds
    ) {
        return ResponseEntity.ok(paymentService.simulateLoad("cpu", seconds));
    }

    @GetMapping("/simulate-memory")
    public ResponseEntity<Object> simulateMemory(
            @RequestParam(value = "mb", defaultValue = "64") int mb
    ) {
        return ResponseEntity.ok(paymentService.simulateLoad("memory", mb));
    }

    @GetMapping("/payments/simulate-memory")
    public ResponseEntity<Object> simulateMemoryViaGateway(
            @RequestParam(value = "mb", defaultValue = "64") int mb
    ) {
        return ResponseEntity.ok(paymentService.simulateLoad("memory", mb));
    }

    @GetMapping("/payments/simulate-load")
    public ResponseEntity<Object> simulateLoadViaGateway(
            @RequestParam(value = "type", defaultValue = "cpu") String type,
            @RequestParam(value = "amount", defaultValue = "2") int amount
    ) {
        return ResponseEntity.ok(paymentService.simulateLoad(type, amount));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/payments/health")
    public ResponseEntity<Map<String, String>> paymentHealth() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/payments/order/{orderId}")
    public ResponseEntity<?> getTransactionsByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getTransactionsByOrder(orderId));
    }
}
