package com.example.inventory.mapper;

import com.example.inventory.dto.request.CreateInventoryItemRequest;
import com.example.inventory.dto.response.InventoryItemResponse;
import com.example.inventory.entity.InventoryItem;
import com.example.inventory.enums.InventoryStatus;

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

    public InventoryItem toEntity(CreateInventoryItemRequest request) {
        InventoryItem item = new InventoryItem();

        item.setProductId(request.getProductId());
        item.setBarcode(request.getBarcode().trim());
        item.setSerialNumber(trimToNull(request.getSerialNumber()));

        item.setActualWeightGrams(request.getActualWeightGrams());
        item.setActualGoldPurityPercent(
                request.getActualGoldPurityPercent()
        );

        item.setCertificateNumber(
                trimToNull(request.getCertificateNumber())
        );

        item.setLocationCode(trimToNull(request.getLocationCode()));

        item.setStatus(InventoryStatus.AVAILABLE);
        item.setReservationReference(null);
        item.setReservedUntil(null);

        return item;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}