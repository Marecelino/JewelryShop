
package com.example.inventory.controller;

import com.example.inventory.dto.response.InventorySummaryResponse;
import com.example.inventory.service.InventoryItemService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory-summaries")
@Validated
public class InventorySummaryController {

    private final InventoryItemService inventoryItemService;

    public InventorySummaryController(
            InventoryItemService inventoryItemService) {
        this.inventoryItemService = inventoryItemService;
    }

    @GetMapping
    public List<InventorySummaryResponse> getInventorySummaries(
            @RequestParam
            @NotEmpty
            List<@NotNull  @Positive Integer> productIds) {

        return inventoryItemService.getInventorySummaries(productIds);
    }
}
