package com.example.order.dto.response;

public record CreateOrderResult(
        OrderResponse order,
        Status status
) {
    public enum Status {
        CREATED,
        EXISTING,
        ACCEPTED
    }

    public boolean isCreated() {
        return status == Status.CREATED;
    }

    public boolean isExisting() {
        return status == Status.EXISTING;
    }

    public boolean isAccepted() {
        return status == Status.ACCEPTED;
    }
}
