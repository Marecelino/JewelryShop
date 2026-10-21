package com.example.inventory.controller;

import com.example.inventory.dto.request.CreateInventoryItemRequest;
import com.example.inventory.dto.request.PageQueryRequest;
import com.example.inventory.dto.response.InventoryItemResponse;
import com.example.inventory.service.InventoryItemService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/inventory-items")
public class InventoryItemController {

    private final InventoryItemService inventoryItemService;

    public InventoryItemController(
            InventoryItemService inventoryItemService
    ) {
        this.inventoryItemService = inventoryItemService;
    }

    /*    @GetMapping
        public ResponseEntity<List<InventoryItemResponse>> getAllItems() {
            return ResponseEntity.ok(
                    inventoryItemService.getAllItems()
            );
        }*/
    @GetMapping
    public PagedModel<InventoryItemResponse> getAllItems(
            @Valid @ModelAttribute PageQueryRequest request
    ) {
        Pageable pageable = PageRequest.of(
                request.getPageNumber(),
                request.getSize(),
                Sort.by(Sort.Direction.ASC, "id")
        );

        Page<InventoryItemResponse> result =
                inventoryItemService.getAllItems(pageable);

        return new PagedModel<>(result);
    }

    //Get item by ID
    @GetMapping("/{id}")
    public ResponseEntity<InventoryItemResponse> getItemById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                inventoryItemService.getItemById(id)
        );
    }

    @PostMapping
    public ResponseEntity<InventoryItemResponse> createItem(
            @Valid @RequestBody CreateInventoryItemRequest request
    ) {
        InventoryItemResponse createdItem =
                inventoryItemService.createItem(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdItem.getInventoryItemId())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(createdItem);
    }
}
