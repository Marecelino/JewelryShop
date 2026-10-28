package com.example.order.exception;

public class ProductUnavailableException extends RuntimeException {
    private final Integer productId;

    public ProductUnavailableException(Integer productId, String message) {
        super(message);
        this.productId = productId;
    }

    public Integer getProductId() {
        return productId;
    }
}
