package com.example.order.exception;

public class ReservationConflictException extends RuntimeException {
    private final String orderReference;
    private final String orderItemReference;

    public ReservationConflictException(String orderReference, String orderItemReference, String message) {
        super(message);
        this.orderReference = orderReference;
        this.orderItemReference = orderItemReference;
    }

    public String getOrderReference() {
        return orderReference;
    }

    public String getOrderItemReference() {
        return orderItemReference;
    }
}
