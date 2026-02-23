package com.example.payment.controller;

import com.example.payment.config.CorrelationIdFilter;
import com.example.payment.dto.ServiceError;
import com.example.payment.service.PaymentException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        FieldError firstError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = firstError != null && firstError.getDefaultMessage() != null
                ? firstError.getDefaultMessage()
                : "Invalid request";
        return build(HttpStatus.BAD_REQUEST, new ServiceError("INVALID_REQUEST", message), request);
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentException(PaymentException exception, HttpServletRequest request) {
        return build(
                HttpStatus.valueOf(exception.getStatus()),
                new ServiceError(exception.getCode(), exception.getMessage()),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception, HttpServletRequest request) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                new ServiceError("INTERNAL_ERROR", "Internal server error"),
                request
        );
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, ServiceError error, HttpServletRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", error);
        payload.put("correlationId", request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        return ResponseEntity.status(status).body(payload);
    }
}
