package com.example.order.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Schema(description = "Create order request payload")
public class CreateOrderRequest {

    @Schema(description = "List of product items to order")
    @NotEmpty(message = "items cannot be empty")
    private List<@NotNull(message = "item cannot be null") @Valid OrderItemRequest> items;

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(List<OrderItemRequest> items) {
        this.items = items;
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }

    @AssertTrue(message = "items list cannot contain null elements")
    @JsonIgnore
    public boolean isItemsNonNull() {
        return items != null && items.stream().allMatch(Objects::nonNull);
    }

    public int calculateTotalQuantity() {
        if (items == null) {
            return 0;
        }
        return items.stream()
                .filter(i -> i != null && i.getQuantity() != null)
                .mapToInt(OrderItemRequest::getQuantity)
                .sum();
    }

    public Map<Integer, Integer> toNormalizedPayload() {
        if (items == null) {
            return Collections.emptyMap();
        }
        Map<Integer, Integer> map = new HashMap<>();
        for (OrderItemRequest item : items) {
            if (item != null && item.getProductId() != null && item.getQuantity() != null) {
                map.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            }
        }
        return map;
    }
}
