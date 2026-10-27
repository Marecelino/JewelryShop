package com.example.inventory.controller;

import com.example.inventory.dto.request.CreateInventoryReservationRequest;
import com.example.inventory.dto.response.InventoryReservationResponse;
import com.example.inventory.dto.response.ReservationCreationResult;
import com.example.inventory.enums.InventoryReservationStatus;
import com.example.inventory.exception.*;
import com.example.inventory.service.InventoryReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InventoryReservationControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private InventoryReservationService inventoryReservationService;

    @InjectMocks
    private InventoryReservationController inventoryReservationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(inventoryReservationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private InventoryReservationResponse buildMockResponse(Long id, InventoryReservationStatus status) {
        return new InventoryReservationResponse(
                id,
                "ORD-1001",
                "ORD-1001-ITEM-1",
                1,
                101L,
                status,
                15,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:15:00Z"),
                status == InventoryReservationStatus.ACTIVE ? null : Instant.parse("2026-01-01T10:05:00Z"),
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:00Z")
        );
    }

    @Nested
    @DisplayName("POST /internal/api/v1/inventory/reservations tests")
    class CreateReservationTests {

        @Test
        @DisplayName("When new reservation created, returns 201 Created and Location header")
        void createReservation_whenNew_returns201AndLocationHeader() throws Exception {
            CreateInventoryReservationRequest request = new CreateInventoryReservationRequest(
                    "ORD-1001", "ORD-1001-ITEM-1", 1, 15);
            InventoryReservationResponse response = buildMockResponse(100L, InventoryReservationStatus.ACTIVE);

            when(inventoryReservationService.reserve(any()))
                    .thenReturn(new ReservationCreationResult(response, true));

            mockMvc.perform(post("/internal/api/v1/inventory/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/internal/api/v1/inventory/reservations/100")))
                    .andExpect(jsonPath("$.reservationId").value(100))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("When reservation already exists with same payload, returns 200 OK (idempotent replay)")
        void createReservation_whenIdempotentRetry_returns200Ok() throws Exception {
            CreateInventoryReservationRequest request = new CreateInventoryReservationRequest(
                    "ORD-1001", "ORD-1001-ITEM-1", 1, 15);
            InventoryReservationResponse response = buildMockResponse(100L, InventoryReservationStatus.ACTIVE);

            when(inventoryReservationService.reserve(any()))
                    .thenReturn(new ReservationCreationResult(response, false));

            mockMvc.perform(post("/internal/api/v1/inventory/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservationId").value(100))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("When request payload is invalid, returns 400 Bad Request")
        void createReservation_whenInvalidPayload_returns400BadRequest() throws Exception {
            CreateInventoryReservationRequest request = new CreateInventoryReservationRequest(
                    "", "", -1, 0);

            mockMvc.perform(post("/internal/api/v1/inventory/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.validationErrors.orderReference").exists())
                    .andExpect(jsonPath("$.validationErrors.orderItemReference").exists())
                    .andExpect(jsonPath("$.validationErrors.productId").exists())
                    .andExpect(jsonPath("$.validationErrors.ttlMinutes").exists());
        }

        @Test
        @DisplayName("When product not found, returns 404 Not Found")
        void createReservation_whenProductNotFound_returns404NotFound() throws Exception {
            CreateInventoryReservationRequest request = new CreateInventoryReservationRequest(
                    "ORD-1001", "ORD-1001-ITEM-1", 9999, 15);

            when(inventoryReservationService.reserve(any()))
                    .thenThrow(new ProductNotFoundException(9999));

            mockMvc.perform(post("/internal/api/v1/inventory/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("Product not found with ID: 9999")));
        }

        @Test
        @DisplayName("When out of stock or payload conflict, returns 409 Conflict")
        void createReservation_whenConflict_returns409Conflict() throws Exception {
            CreateInventoryReservationRequest request = new CreateInventoryReservationRequest(
                    "ORD-1001", "ORD-1001-ITEM-1", 1, 15);

            when(inventoryReservationService.reserve(any()))
                    .thenThrow(new ReservationConflictException("No available inventory item for product ID: 1"));

            mockMvc.perform(post("/internal/api/v1/inventory/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(containsString("No available inventory item")));
        }

        @Test
        @DisplayName("When downstream Product Service is unavailable, returns 503 Service Unavailable")
        void createReservation_whenDownstreamUnavailable_returns503() throws Exception {
            CreateInventoryReservationRequest request = new CreateInventoryReservationRequest(
                    "ORD-1001", "ORD-1001-ITEM-1", 1, 15);

            when(inventoryReservationService.reserve(any()))
                    .thenThrow(new DownstreamServiceUnavailableException("Product Service is down"));

            mockMvc.perform(post("/internal/api/v1/inventory/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.message").value(containsString("Product Service is down")));
        }
    }

    @Nested
    @DisplayName("GET /internal/api/v1/inventory/reservations/{reservationId} tests")
    class GetReservationByIdTests {

        @Test
        @DisplayName("When reservation exists, returns 200 OK")
        void getReservationById_whenExists_returns200Ok() throws Exception {
            InventoryReservationResponse response = buildMockResponse(100L, InventoryReservationStatus.ACTIVE);
            when(inventoryReservationService.getReservationById(100L)).thenReturn(response);

            mockMvc.perform(get("/internal/api/v1/inventory/reservations/100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservationId").value(100))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("When reservation does not exist, returns 404 Not Found")
        void getReservationById_whenMissing_returns404NotFound() throws Exception {
            when(inventoryReservationService.getReservationById(999L))
                    .thenThrow(new ReservationNotFoundException(999L));

            mockMvc.perform(get("/internal/api/v1/inventory/reservations/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("POST /internal/api/v1/inventory/reservations/{reservationId}/commit tests")
    class CommitReservationTests {

        @Test
        @DisplayName("When commit succeeds, returns 200 OK with COMMITTED status")
        void commitReservation_whenValid_returns200Ok() throws Exception {
            InventoryReservationResponse response = buildMockResponse(100L, InventoryReservationStatus.COMMITTED);
            when(inventoryReservationService.commitReservation(100L)).thenReturn(response);

            mockMvc.perform(post("/internal/api/v1/inventory/reservations/100/commit"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMMITTED"));
        }

        @Test
        @DisplayName("When reservation is expired or released, returns 409 Conflict")
        void commitReservation_whenConflict_returns409Conflict() throws Exception {
            when(inventoryReservationService.commitReservation(100L))
                    .thenThrow(new ReservationConflictException("Reservation has expired and stock was returned to inventory"));

            mockMvc.perform(post("/internal/api/v1/inventory/reservations/100/commit"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(containsString("Reservation has expired")));
        }
    }

    @Nested
    @DisplayName("POST /internal/api/v1/inventory/reservations/{reservationId}/release tests")
    class ReleaseReservationTests {

        @Test
        @DisplayName("When release succeeds, returns 200 OK with RELEASED status")
        void releaseReservation_whenValid_returns200Ok() throws Exception {
            InventoryReservationResponse response = buildMockResponse(100L, InventoryReservationStatus.RELEASED);
            when(inventoryReservationService.releaseReservation(100L)).thenReturn(response);

            mockMvc.perform(post("/internal/api/v1/inventory/reservations/100/release"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RELEASED"));
        }

        @Test
        @DisplayName("When releasing already committed or expired reservation, returns 409 Conflict")
        void releaseReservation_whenConflict_returns409Conflict() throws Exception {
            when(inventoryReservationService.releaseReservation(100L))
                    .thenThrow(new ReservationConflictException("Cannot release already committed/sold reservation"));

            mockMvc.perform(post("/internal/api/v1/inventory/reservations/100/release"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }
    }
}
