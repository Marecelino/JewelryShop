package com.example.order.exception;

public class OrderNotFoundException extends RuntimeException {
    private final String orderReference;

    public OrderNotFoundException(String orderReference) {
        super("Order not found with reference: " + orderReference);
        this.orderReference = orderReference;
    }

    public String getOrderReference() {
        return orderReference;
    }
}
