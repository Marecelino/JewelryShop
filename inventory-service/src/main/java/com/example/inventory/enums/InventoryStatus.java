package com.example.inventory.enums;


public enum InventoryStatus {
    AVAILABLE("Available to sell"),
    RESERVED("Reserved"),
    SOLD("Sold"),
    DAMAGED("Damaged");

    private final String description;
    InventoryStatus(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }
}
