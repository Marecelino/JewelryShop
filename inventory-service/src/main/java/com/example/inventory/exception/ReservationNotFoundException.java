package com.example.inventory.exception;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(Long reservationId) {
        super("Reservation not found with ID: " + reservationId);
    }
}
