package com.example.product.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Xử lý tập trung tất cả exception trong Product Service.
 *
 * <p>Không để stack trace, tên class exception hoặc thông tin kết nối database
 * xuất hiện trong response body. Log đầy đủ ở server để debug.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // 400 Bad Request — validation lỗi
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                errors.putIfAbsent(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "Invalid value"
                )
        );

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Request validation failed",
                request.getRequestURI(),
                errors
        );

        return ResponseEntity.badRequest().body(response);
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request — JSON không đọc được
    // -------------------------------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequestBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Request body is missing or contains invalid JSON",
                request.getRequestURI(),
                Map.of()
        );

        return ResponseEntity.badRequest().body(response);
    }

    // -------------------------------------------------------------------------
    // 4xx / 5xx từ Product Service logic — ResponseStatusException
    // Bao gồm 404 Not Found và 503 Service Unavailable do InventoryClient
    // -------------------------------------------------------------------------

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {

        HttpStatusCode status = ex.getStatusCode();
        HttpStatus resolvedStatus = HttpStatus.resolve(status.value());

        String error = resolvedStatus != null
                ? resolvedStatus.getReasonPhrase()
                : "HTTP Error";

        String message = ex.getReason() != null
                ? ex.getReason()
                : error;

        // Log lỗi 5xx để debug server-side, không log 4xx thường xuyên
        if (status.is5xxServerError()) {
            log.warn("Service error [{}] on {}: {}", status.value(),
                    request.getRequestURI(), message);
        }

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                error,
                message,
                request.getRequestURI(),
                Map.of()
        );

        return ResponseEntity.status(status).body(response);
    }

    // -------------------------------------------------------------------------
    // 503 Service Unavailable — Inventory Service timeout hoặc connection refused
    // -------------------------------------------------------------------------

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceAccessException(
            ResourceAccessException ex,
            HttpServletRequest request) {

        log.warn("Cannot reach Inventory Service on {}: {}", request.getRequestURI(),
                ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "Inventory Service is currently unavailable. Please try again later.",
                request.getRequestURI(),
                Map.of()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    // -------------------------------------------------------------------------
    // 502 Bad Gateway — Inventory Service trả HTTP lỗi không mong đợi
    // -------------------------------------------------------------------------

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleRestClientResponseException(
            RestClientResponseException ex,
            HttpServletRequest request) {

        log.warn("Inventory Service returned error {} on {}: {}",
                ex.getStatusCode(), request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_GATEWAY.value(),
                HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                "Received an unexpected error response from Inventory Service.",
                request.getRequestURI(),
                Map.of()
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    // -------------------------------------------------------------------------
    // 500 Internal Server Error — lỗi không dự kiến
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred. Please contact support.",
                request.getRequestURI(),
                Map.of()
        );

        return ResponseEntity.internalServerError().body(response);
    }
}