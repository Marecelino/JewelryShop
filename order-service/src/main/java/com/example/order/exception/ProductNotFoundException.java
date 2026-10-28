package com.example.order.exception;

public class ProductNotFoundException extends RuntimeException {
    private final Integer productId;

    public ProductNotFoundException(Integer productId) {
        super("Product not found with id: " + productId);
        this.productId = productId;
    }

    public Integer getProductId() {
        return productId;
    }
}
