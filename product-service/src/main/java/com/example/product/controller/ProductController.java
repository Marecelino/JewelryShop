package com.example.product.controller;

import com.example.product.config.NotFoundApiResponse;
import com.example.product.config.StandardApiResponses;
import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.request.UpdateProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@Validated
@Tag(name = "Products - API V1", description = "Product catalog management")
public class ProductController {

    private final ProductService productService;
    private static final int PAGE_SIZE = 3;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/products
    // -------------------------------------------------------------------------
    @GetMapping
    @Operation(
            summary = "Get all products",
            description = "Returns all products with inventory summary from Inventory Service."
    )
    @StandardApiResponses
    @ApiResponse(responseCode = "200", description = "Products retrieved successfully",
            useReturnTypeSchema = true)
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }

    // -------------------------------------------------------------------------
    // HEAD /api/v1/products/{id}
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/{id}", method = RequestMethod.HEAD)
    @Operation(
            summary = "Check if product exists",
            description = "Checks if a product exists by ID without fetching the full product data."
    )
    @ApiResponse(responseCode = "200", description = "Product exists")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<Void> checkProductExists(@PathVariable Integer id) {
        if (productService.existsById(id)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/products/{id}
    // -------------------------------------------------------------------------
    @GetMapping("/{id}")
    @Operation(
            summary = "Get product by ID",
            description = "Retrieves a single product with its inventory summary."
    )
    @StandardApiResponses
    @NotFoundApiResponse
    @ApiResponse(responseCode = "200", description = "Product found", useReturnTypeSchema = true)
    public ProductResponse getProductById(@PathVariable Integer id) {
        return productService.getProductById(id);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/products/search?category=...
    // -------------------------------------------------------------------------
    @GetMapping("/search")
    @Operation(
            summary = "Search products by category",
            description = "Returns products matching the given category with inventory summary."
    )
    public List<ProductResponse> searchProducts(@RequestParam String category) {
        return productService.searchByCategory(category);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/products
    // -------------------------------------------------------------------------
    @PostMapping
    @Operation(
            summary = "Create a new product",
            description = """
                    Creates a product in the product catalog.
                    Inventory items must be registered separately via Inventory Service
                    (POST /api/v1/inventory-items).
                    The 'inventory' field in the response will be null until inventory items are added.
                    """
    )
    @StandardApiResponses
    @ApiResponse(responseCode = "201", description = "Product created successfully",
            useReturnTypeSchema = true)
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        ProductResponse response = productService.createProduct(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getProductId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/products/{id}
    // -------------------------------------------------------------------------
    @PutMapping("/{id}")
    @Operation(
            summary = "Update a product",
            description = "Updates product catalog information. Does not affect inventory."
    )
    @StandardApiResponses
    @NotFoundApiResponse
    @ApiResponse(responseCode = "200", description = "Product updated successfully",
            useReturnTypeSchema = true)
    public ProductResponse updateProduct(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateProductRequest request) {

        return productService.updateProduct(id, request);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/products/{id}
    // -------------------------------------------------------------------------
    @DeleteMapping("/{id}")
    @Operation(
            summary = "Deactivate a product",
            description = """
                    Sets the product as inactive (active=false). This is a soft delete.
                    The product record is kept to avoid orphaning inventory items in Inventory Service.
                    Inventory items are NOT deleted automatically.
                    """
    )
    @NotFoundApiResponse
    @ApiResponse(responseCode = "204", description = "Product deactivated successfully")
    public ResponseEntity<Void> deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/products/test-pagination
    // Giữ nguyên endpoint này để tương thích với script test hiện có
    // -------------------------------------------------------------------------
    @GetMapping("/test-pagination")
    @Operation(
            summary = "Get products with pagination (v1, for testing)",
            description = "Paginated product list. Use /api/v2/products for production pagination."
    )
    public Page<ProductResponse> getAllProductsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return productService.getAllProducts(pageable);
    }
}