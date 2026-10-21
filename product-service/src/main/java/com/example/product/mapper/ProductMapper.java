package com.example.product.mapper;

import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.request.UpdateProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.Product;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper chuyển đổi giữa Product Entity và các Product DTO.
 *
 * <p>Mapper chỉ xử lý dữ liệu thuộc Product Service. Không gọi HTTP,
 * không đọc tồn kho, không tương tác với Inventory Service. Trường
 * {@code inventory} trong ProductResponse được gán ở tầng Service.</p>
 */
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
        product.setDescription(request.getDescription());

        // productId, createdAt và active được quản lý bởi application.
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
        product.setDescription(request.getDescription());
        product.setActive(request.getActive());
    }

    // Convert Product Entity -> ProductResponse (không có inventory)
    public ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();

        response.setProductId(product.getProductId());
        response.setProductName(product.getProductName());
        response.setCategory(product.getCategory());
        response.setMaterial(product.getMaterial());
        response.setGemstone(product.getGemstone());
        response.setPrice(product.getPrice());
        response.setDescription(product.getDescription());
        response.setCreatedAt(product.getCreatedAt());
        response.setActive(product.getActive());
        // inventory field để null — Service layer sẽ gán sau khi gọi Inventory Service

        return response;
    }

    // Convert a list of Entities -> a list of response DTOs (không có inventory)
    public List<ProductResponse> toResponseList(List<Product> products) {
        return products.stream()
                .map(this::toResponse)
                .toList();
    }
}