package com.example.order.exception;

public class ReservationNotFoundException extends RuntimeException {
    private final Long reservationId;

    public ReservationNotFoundException(Long reservationId) {
        super("Reservation not found with id: " + reservationId);
        this.reservationId = reservationId;
    }

    public Long getReservationId() {
        return reservationId;
    }
}
