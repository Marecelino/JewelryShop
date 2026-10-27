package com.example.inventory.controller;

import com.example.inventory.dto.request.CreateInventoryReservationRequest;
import com.example.inventory.dto.response.InventoryReservationResponse;
import com.example.inventory.dto.response.ReservationCreationResult;
import com.example.inventory.service.InventoryReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/internal/api/v1/inventory/reservations")
public class InventoryReservationController {

    private final InventoryReservationService inventoryReservationService;

    public InventoryReservationController(InventoryReservationService inventoryReservationService) {
        this.inventoryReservationService = inventoryReservationService;
    }

    @PostMapping
    public ResponseEntity<InventoryReservationResponse> createReservation(
            @Valid @RequestBody CreateInventoryReservationRequest request
    ) {
        ReservationCreationResult result = inventoryReservationService.reserve(request);

        if (result.newlyCreated()) {
            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(result.response().reservationId())
                    .toUri();

            return ResponseEntity
                    .created(location)
                    .body(result.response());
        }

        // Idempotent retry: reservation already exists with identical payload
        return ResponseEntity.ok(result.response());
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<InventoryReservationResponse> getReservationById(
            @PathVariable Long reservationId
    ) {
        InventoryReservationResponse response = inventoryReservationService.getReservationById(reservationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reservationId}/commit")
    public ResponseEntity<InventoryReservationResponse> commitReservation(
            @PathVariable Long reservationId
    ) {
        InventoryReservationResponse response = inventoryReservationService.commitReservation(reservationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reservationId}/release")
    public ResponseEntity<InventoryReservationResponse> releaseReservation(
            @PathVariable Long reservationId
    ) {
        InventoryReservationResponse response = inventoryReservationService.releaseReservation(reservationId);
        return ResponseEntity.ok(response);
    }
}
