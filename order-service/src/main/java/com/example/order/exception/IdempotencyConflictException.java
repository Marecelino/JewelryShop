package com.example.order.exception;

public class IdempotencyConflictException extends RuntimeException {
    private final String idempotencyKey;

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key '" + idempotencyKey + "' was already used with a different request payload");
        this.idempotencyKey = idempotencyKey;
    }

    public IdempotencyConflictException(String idempotencyKey, String message) {
        super(message);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
