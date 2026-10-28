package com.example.order.dto.external;

public record CreateReservationRequestDto(
        String orderReference,
        String orderItemReference,
        Integer productId,
        Integer ttlMinutes
) {
}
