package com.example.order.client;

import com.example.order.dto.external.CreateReservationRequestDto;
import com.example.order.dto.external.InventoryReservationDto;
import com.example.order.exception.DownstreamServiceException;
import com.example.order.exception.ProductNotFoundException;
import com.example.order.exception.ReservationConflictException;
import com.example.order.exception.ReservationNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class InventoryReservationClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationClient.class);

    private final RestClient inventoryRestClient;
    private final ObjectMapper objectMapper;

    public InventoryReservationClient(
            @Qualifier("inventoryRestClient") RestClient inventoryRestClient,
            ObjectMapper objectMapper
    ) {
        this.inventoryRestClient = inventoryRestClient;
        this.objectMapper = objectMapper;
    }

    public InventoryReservationDto reserve(CreateReservationRequestDto request) {
        try {
            return inventoryRestClient.post()
                    .uri("/internal/api/v1/inventory/reservations")
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (clientRequest, response) -> {
                        String body = new String(response.getBody().readAllBytes());
                        String errorMessage = extractMessage(body, response.getStatusText());
                        int statusCode = response.getStatusCode().value();

                        if (statusCode == 404) {
                            throw new ProductNotFoundException(request.productId());
                        }
                        if (statusCode == 409) {
                            throw new ReservationConflictException(
                                    request.orderReference(),
                                    request.orderItemReference(),
                                    errorMessage
                            );
                        }
                        if (statusCode == 401 || statusCode == 403) {
                            throw new DownstreamServiceException(
                                    "inventory-service",
                                    statusCode,
                                    "Internal authentication failed for inventory-service: " + errorMessage
                            );
                        }
                        throw new HttpClientErrorException(response.getStatusCode(), errorMessage);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (clientRequest, response) -> {
                        throw new DownstreamServiceException(
                                "inventory-service",
                                response.getStatusCode().value(),
                                "Inventory service error: " + response.getStatusText()
                        );
                    })
                    .body(InventoryReservationDto.class);
        } catch (ProductNotFoundException | ReservationConflictException | DownstreamServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.error("Network or timeout error calling inventory-service reserve for item {}: {}",
                    request.orderItemReference(), e.getMessage());
            throw new DownstreamServiceException("inventory-service", "Timeout or connection failure calling Inventory Service reserve", e);
        } catch (HttpClientErrorException e) {
            log.error("Client error calling inventory-service reserve: status={}", e.getStatusCode());
            throw new DownstreamServiceException("inventory-service", e.getStatusCode().value(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling inventory-service reserve: {}", e.getMessage(), e);
            throw new DownstreamServiceException("inventory-service", "Unexpected failure calling Inventory Service reserve", e);
        }
    }

    public InventoryReservationDto getReservationById(Long reservationId) {
        try {
            return inventoryRestClient.get()
                    .uri("/internal/api/v1/inventory/reservations/{reservationId}", reservationId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (clientRequest, response) -> {
                        int statusCode = response.getStatusCode().value();
                        if (statusCode == 404) {
                            throw new ReservationNotFoundException(reservationId);
                        }
                        if (statusCode == 401 || statusCode == 403) {
                            throw new DownstreamServiceException(
                                    "inventory-service",
                                    statusCode,
                                    "Internal authentication failed for inventory-service"
                            );
                        }
                        throw new HttpClientErrorException(response.getStatusCode(), response.getStatusText());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (clientRequest, response) -> {
                        throw new DownstreamServiceException(
                                "inventory-service",
                                response.getStatusCode().value(),
                                "Inventory service error: " + response.getStatusText()
                        );
                    })
                    .body(InventoryReservationDto.class);
        } catch (ReservationNotFoundException | DownstreamServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.error("Network or timeout error calling inventory-service getReservationById({}): {}", reservationId, e.getMessage());
            throw new DownstreamServiceException("inventory-service", "Timeout or connection failure calling getReservationById", e);
        } catch (HttpClientErrorException e) {
            throw new DownstreamServiceException("inventory-service", e.getStatusCode().value(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling inventory-service getReservationById({}): {}", reservationId, e.getMessage(), e);
            throw new DownstreamServiceException("inventory-service", "Unexpected failure calling getReservationById", e);
        }
    }

    public InventoryReservationDto releaseReservation(Long reservationId) {
        try {
            return inventoryRestClient.post()
                    .uri("/internal/api/v1/inventory/reservations/{reservationId}/release", reservationId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (clientRequest, response) -> {
                        String body = new String(response.getBody().readAllBytes());
                        String errorMessage = extractMessage(body, response.getStatusText());
                        int statusCode = response.getStatusCode().value();

                        if (statusCode == 404) {
                            throw new ReservationNotFoundException(reservationId);
                        }
                        if (statusCode == 409) {
                            throw new ReservationConflictException(null, null, errorMessage);
                        }
                        if (statusCode == 401 || statusCode == 403) {
                            throw new DownstreamServiceException(
                                    "inventory-service",
                                    statusCode,
                                    "Internal authentication failed for inventory-service: " + errorMessage
                            );
                        }
                        throw new HttpClientErrorException(response.getStatusCode(), errorMessage);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (clientRequest, response) -> {
                        throw new DownstreamServiceException(
                                "inventory-service",
                                response.getStatusCode().value(),
                                "Inventory service error: " + response.getStatusText()
                        );
                    })
                    .body(InventoryReservationDto.class);
        } catch (ReservationNotFoundException | ReservationConflictException | DownstreamServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.error("Network or timeout error calling inventory-service release for reservationId={}: {}", reservationId, e.getMessage());
            throw new DownstreamServiceException("inventory-service", "Timeout or connection failure calling releaseReservation", e);
        } catch (HttpClientErrorException e) {
            throw new DownstreamServiceException("inventory-service", e.getStatusCode().value(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling inventory-service release for reservationId={}: {}", reservationId, e.getMessage(), e);
            throw new DownstreamServiceException("inventory-service", "Unexpected failure calling releaseReservation", e);
        }
    }

    private String extractMessage(String jsonBody, String fallback) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            if (root.has("message") && !root.get("message").isNull()) {
                return root.get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
