package com.example.payment.dto;

public class ServiceError {

    private final String code;
    private final String message;

    public ServiceError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
