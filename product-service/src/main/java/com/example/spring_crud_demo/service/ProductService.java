package com.example.spring_crud_demo.service;

import com.example.spring_crud_demo.dto.request.CreateProductRequest;
import com.example.spring_crud_demo.dto.request.UpdateProductRequest;
import com.example.spring_crud_demo.dto.response.ProductResponse;
import com.example.spring_crud_demo.entity.Product;
import com.example.spring_crud_demo.mapper.ProductMapper;
import com.example.spring_crud_demo.repository.ProductRepository;

import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(
            ProductRepository productRepository,
            ProductMapper productMapper) {

        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    // READ: Get all products
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {

        List<Product> products = productRepository.findAll();

        return productMapper.toResponseList(products);
    }

    // READ: Get a product by ID
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Integer productId) {

        Product product = findProductEntityById(productId);

        return productMapper.toResponse(product);
    }

    // CREATE: Create a new product
    @Transactional
    public ProductResponse createProduct(
           CreateProductRequest request) {

        Product product = productMapper.toEntity(request);

        // These values are managed by the application.
        product.setCreatedAt(LocalDateTime.now());
        product.setActive(true);

        Product savedProduct = productRepository.save(product);

        return productMapper.toResponse(savedProduct);
    }

    public void create(@NotNull CreateProductRequest request) {
        Product product = productMapper.toEntity(request);
        product.setCreatedAt(LocalDateTime.now());
        product.setActive(true);
        productRepository.save(product);
    }

    // UPDATE: Update an existing product
    @Transactional
    public ProductResponse updateProduct(
            Integer productId,
            UpdateProductRequest request) {

        Product existingProduct = findProductEntityById(productId);

        productMapper.updateEntity(existingProduct, request);

        Product updatedProduct =
                productRepository.save(existingProduct);

        return productMapper.toResponse(updatedProduct);
    }

    // DELETE: Delete a product
    @Transactional
    public void deleteProduct(Integer productId) {

        Product product = findProductEntityById(productId);

        productRepository.delete(product);
    }

    // Find the Entity or return 404
    private Product findProductEntityById(Integer productId) {

        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found: " + productId
                ));
    }
    // search products by category
    public List<ProductResponse> searchByCategory(String category) {
        List<Product> products =
                productRepository.findByCategory(category);

        return products.stream()
                .map(productMapper::toResponse)
                .toList();
    }

    //pagination getAllProducts
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(productMapper::toResponse);
    }
}