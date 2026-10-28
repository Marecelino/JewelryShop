package com.example.order.dto.external;

import java.math.BigDecimal;

public record ProductDetailDto(
        Integer productId,
        String productName,
        BigDecimal price,
        Boolean active
) {
}
