package com.example.inventory.mapper;

import com.example.inventory.dto.response.InventoryItemResponse;
import com.example.inventory.entity.InventoryItem;

import org.springframework.stereotype.Component;

@Component
public class InventoryItemMapper {

    public InventoryItemResponse toResponse(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(),
                item.getProductId(),
                item.getBarcode(),
                item.getSerialNumber(),
                item.getActualWeightGrams(),
                item.getActualGoldPurityPercent(),
                item.getCertificateNumber(),
                item.getLocationCode(),
                item.getStatus(),
                item.getReservationReference(),
                item.getReservedUntil(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}