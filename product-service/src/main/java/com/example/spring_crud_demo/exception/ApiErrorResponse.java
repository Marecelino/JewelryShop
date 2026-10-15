package com.example.spring_crud_demo.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Standard API error response")
public class ApiErrorResponse {

    @Schema(description = "Time when the error occurred")
    private final Instant timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private final int status;

    @Schema(description = "HTTP error reason", example = "Bad Request")
    private final String error;

    @Schema(
            description = "Detailed error message",
            example = "Request validation failed"
    )
    private final String message;

    @Schema(
            description = "Request path",
            example = "/api/v1/products/1"
    )
    private final String path;

    @Schema(description = "Validation errors mapped by field name")
    private final Map<String, String> validationErrors;

    public ApiErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            Map<String, String> validationErrors) {

        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.validationErrors = validationErrors;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }
}