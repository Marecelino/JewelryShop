package com.example.order.dto.response;

public record CancelOrderResult(
        OrderResponse order,
        boolean fullyCancelled
) {
}
