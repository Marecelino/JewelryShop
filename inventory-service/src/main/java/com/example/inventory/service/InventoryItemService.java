package com.example.inventory.service;

import com.example.inventory.dto.request.CreateInventoryItemRequest;
import com.example.inventory.dto.response.InventoryItemResponse;
import com.example.inventory.dto.response.InventorySummaryResponse;
import com.example.inventory.entity.InventoryItem;
import com.example.inventory.enums.InventoryStatus;
import com.example.inventory.exception.InventoryItemConflictException;
import com.example.inventory.exception.ProductNotFoundException;
import com.example.inventory.mapper.InventoryItemMapper;
import com.example.inventory.repository.InventoryItemRepository;
import com.example.inventory.repository.InventorySummaryProjection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.inventory.client.ProductClient;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryItemService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryItemMapper inventoryItemMapper;
    private final ProductClient productClient;
    public InventoryItemService(
            InventoryItemRepository inventoryItemRepository,
            InventoryItemMapper inventoryItemMapper,
            ProductClient productClient
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryItemMapper = inventoryItemMapper;
        this.productClient = productClient;
    }

/*    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getAllItems() {
        return inventoryItemRepository.findAll()
                .stream()
                .map(inventoryItemMapper::toResponse)
                .toList();
    }*/
    @Transactional(readOnly = true)
    public Page<InventoryItemResponse> getAllItems(Pageable pageable) {
        return inventoryItemRepository.findAll(pageable)
                .map(inventoryItemMapper::toResponse);
    }
    // Get an inventory item by ID
    @Transactional(readOnly = true)
    public InventoryItemResponse getItemById(Long id) {
        return inventoryItemRepository.findById(id)
                .map(inventoryItemMapper::toResponse)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Inventory item not found with id: " + id
                        )
                );
    }

    @Transactional
    public InventoryItemResponse createItem(
            CreateInventoryItemRequest request
    ) {
        // Step 1: Check whether the Product exists in Product Service.
        if (!productClient.existsById(request.getProductId())) {
            throw new ProductNotFoundException(
                    request.getProductId()
            );
        }

        // Step 2: Convert request DTO to entity.
        InventoryItem item = inventoryItemMapper.toEntity(request);

        // Step 3: Check duplicate barcode before saving.
        if (inventoryItemRepository.existsByBarcode(item.getBarcode())) {
            throw new InventoryItemConflictException(
                    "Barcode already exists: " + item.getBarcode()
            );
        }

        // Step 4: Serial number is optional.
        if (item.getSerialNumber() != null
                && inventoryItemRepository.existsBySerialNumber(
                item.getSerialNumber()
        )) {

            throw new InventoryItemConflictException(
                    "Serial number already exists: "
                            + item.getSerialNumber()
            );
        }

        // Step 5: Save inventory item.
        try {
            InventoryItem savedItem =
                    inventoryItemRepository.saveAndFlush(item);

            return inventoryItemMapper.toResponse(savedItem);

        } catch (DataIntegrityViolationException exception) {
            // Handle database integrity conflicts.
            throw new InventoryItemConflictException(
                    "Inventory item conflicts with existing data "
                            + "or violates a database constraint."
            );
        }
    }
    //getInventorySummaries
    public List<InventorySummaryResponse> getInventorySummaries(
            Collection<Integer> productIds) {

        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "productIds must not be empty"
            );
        }

        if (productIds.stream().anyMatch(
                id -> id == null || id <= 0)) {
            throw new IllegalArgumentException(
                    "productIds must contain positive IDs"
            );
        }

        List<Integer> uniqueProductIds = productIds.stream()
                .distinct()
                .toList();

        List<InventorySummaryProjection> projections =
                inventoryItemRepository.summarizeByProductIds(
                        uniqueProductIds,
                        InventoryStatus.AVAILABLE,
                        InventoryStatus.RESERVED,
                        InventoryStatus.SOLD,
                        InventoryStatus.DAMAGED
                );

        Map<Integer, InventorySummaryProjection> summariesByProductId =
                projections.stream().collect(
                        Collectors.toMap(
                                InventorySummaryProjection::getProductId,
                                Function.identity()
                        )
                );

        return uniqueProductIds.stream()
                .map(productId -> {
                    InventorySummaryProjection summary =
                            summariesByProductId.get(productId);

                    if (summary == null) {
                        return new InventorySummaryResponse(
                                productId, 0L, 0L, 0L, 0L
                        );
                    }

                    return new InventorySummaryResponse(
                            summary.getProductId(),
                            valueOrZero(summary.getAvailableQuantity()),
                            valueOrZero(summary.getReservedQuantity()),
                            valueOrZero(summary.getSoldQuantity()),
                            valueOrZero(summary.getDamagedQuantity())
                    );
                })
                .toList();
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

}
