package com.example.product.controller;

import com.example.product.dto.request.ProductPageRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.service.ProductService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/products")
@Tag(
        name = "Products - API V2",
        description = "Test paging using page model and @ModelAttribute, API version 2"
)
public class ProductV2Controller {

    private static final int PAGE_SIZE = 3;

    private final ProductService productService;

    public ProductV2Controller(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PagedModel<ProductResponse> getAllProducts(
            @Valid @ModelAttribute ProductPageRequest request) {

        Pageable pageable = PageRequest.of(
                request.getPage(),
                PAGE_SIZE,
                Sort.by(Sort.Direction.ASC, "productId")
        );

        Page<ProductResponse> result =
                productService.getAllProducts(pageable);

        return new PagedModel<>(result);
    }
}