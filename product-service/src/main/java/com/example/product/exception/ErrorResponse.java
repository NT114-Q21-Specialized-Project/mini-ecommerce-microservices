package com.example.product.exception;

import java.time.Instant;

public class ErrorResponse {
    private ErrorPayload error;
    private String path;
    private Instant timestamp;

    public ErrorResponse(String code, String message, String path) {
        this.error = new ErrorPayload(code, message);
        this.path = path;
        this.timestamp = Instant.now();
    }

    public ErrorPayload getError() {
        return error;
    }

    public String getPath() {
        return path;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public static class ErrorPayload {
        private String code;
        private String message;

        public ErrorPayload(String code, String message) {
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
}
