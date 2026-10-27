package com.example.inventory.exception;

public class DownstreamServiceUnavailableException extends RuntimeException {

    public DownstreamServiceUnavailableException(String message) {
        super(message);
    }
}
