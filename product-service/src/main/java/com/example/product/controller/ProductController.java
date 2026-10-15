package com.example.product.controller;

import com.example.product.config.NotFoundApiResponse;
import com.example.product.config.StandardApiResponses;
import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.request.UpdateProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
//pagination import
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

@RestController
//@Controller
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {

    private final ProductService productService;
    private static final int PAGE_SIZE = 3;
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // GET: Get all products
    @GetMapping
    @Operation(
            summary = "Get products",
            description = "Returns a page of products. Category is an optional filter."
    )
    @StandardApiResponses
    @ApiResponse(
            responseCode = "200",
            description = "Products retrieved successfully",
            useReturnTypeSchema = true
    )
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }

    // GET: Get product by ID
    @GetMapping("/{id}")
    @Operation(
            summary = "Get product by ID",
            description = "Retrieves a product using its unique identifier."
    )
    @StandardApiResponses
    @NotFoundApiResponse
    @ApiResponse(
            responseCode = "200",
            description = "Product found",
            useReturnTypeSchema = true
    )
    public ProductResponse getProductById(
            @PathVariable Integer id) {

        return productService.getProductById(id);
    }
   // search products by category using request parameter
   @GetMapping("/search")
   public List<ProductResponse> searchProducts(
           @RequestParam String category) {

       return productService.searchByCategory(category);
   }

    // POST: Create product
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        ProductResponse response =
                productService.createProduct(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // PUT: Update product
    @PutMapping("/{id}")
    public ProductResponse updateProduct(
            @PathVariable Integer id,
            //@Valid

            @RequestBody UpdateProductRequest request) {

        return productService.updateProduct(id, request);
    }

    // DELETE: Delete product
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Integer id) {

        productService.deleteProduct(id);

        return ResponseEntity.noContent().build();
    }

    //GET : get all products using pagination
    @GetMapping("/test-pagination")
    public Page<ProductResponse> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return productService.getAllProducts(pageable);
    }
}