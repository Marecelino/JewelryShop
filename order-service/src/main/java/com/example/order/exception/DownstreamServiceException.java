package com.example.order.exception;

public class DownstreamServiceException extends RuntimeException {
    private final String serviceName;
    private final int statusCode;

    public DownstreamServiceException(String serviceName, int statusCode, String message) {
        super(String.format("Downstream service '%s' returned status %d: %s", serviceName, statusCode, message));
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public DownstreamServiceException(String serviceName, String message, Throwable cause) {
        super(String.format("Downstream service '%s' call failed: %s", serviceName, message), cause);
        this.serviceName = serviceName;
        this.statusCode = 503;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
