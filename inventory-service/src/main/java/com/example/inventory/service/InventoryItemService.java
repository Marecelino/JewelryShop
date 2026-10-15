package com.example.inventory.service;

import com.example.inventory.dto.response.InventoryItemResponse;
import com.example.inventory.mapper.InventoryItemMapper;
import com.example.inventory.repository.InventoryItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryItemService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryItemMapper inventoryItemMapper;

    public InventoryItemService(
            InventoryItemRepository inventoryItemRepository,
            InventoryItemMapper inventoryItemMapper
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryItemMapper = inventoryItemMapper;
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
}
