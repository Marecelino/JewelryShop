package com.example.inventory.exception;

public class InventoryItemConflictException extends RuntimeException {

    public InventoryItemConflictException(String message) {
        super(message);
    }
}
