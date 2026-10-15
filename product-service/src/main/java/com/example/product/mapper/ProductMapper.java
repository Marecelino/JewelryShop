package com.example.product.mapper;

import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.request.UpdateProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.Product;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductMapper {

    // Convert CreateProductRequest -> Product Entity
    public Product toEntity(CreateProductRequest request) {
        Product product = new Product();

        product.setProductName(request.getProductName());
        product.setCategory(request.getCategory());
        product.setMaterial(request.getMaterial());
        product.setGemstone(request.getGemstone());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setDescription(request.getDescription());

        // productId, createdAt and active are managed by the application.
        return product;
    }

    // Copy update fields into an existing Entity
    public void updateEntity(
            Product product,
            UpdateProductRequest request) {

        product.setProductName(request.getProductName());
        product.setCategory(request.getCategory());
        product.setMaterial(request.getMaterial());
        product.setGemstone(request.getGemstone());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setDescription(request.getDescription());
        product.setActive(request.getActive());
    }

    // Convert Product Entity -> ProductResponse
    public ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();

        response.setProductId(product.getProductId());
        response.setProductName(product.getProductName());
        response.setCategory(product.getCategory());
        response.setMaterial(product.getMaterial());
        response.setGemstone(product.getGemstone());
        response.setPrice(product.getPrice());
        response.setStockQuantity(product.getStockQuantity());
        response.setDescription(product.getDescription());
        response.setCreatedAt(product.getCreatedAt());
        response.setActive(product.getActive());

        return response;
    }

    // Convert a list of Entities -> a list of response DTOs
    public List<ProductResponse> toResponseList(List<Product> products) {
        return products.stream()
                .map(this::toResponse)
                .toList();
    }
}