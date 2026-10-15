package com.example.product.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
@Table(name = "Products", schema = "dbo")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ProductId")
    private Integer productId;

    @Column(name = "ProductName", nullable = false, length = 100)
    private String productName;

    @Column(name = "Category", nullable = false, length = 50)
    private String category;

    @Column(name = "Material", nullable = false, length = 50)
    private String material;

    @Column(name = "Gemstone", length = 50)
    private String gemstone;

    @PositiveOrZero(message = "Price must be >=0 ")
    @Column(name = "Price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @PositiveOrZero(message = " Stock quantity mustbe >= 0")
    @Column(name = "StockQuantity", nullable = false)
    private Integer stockQuantity;

    @Column(name = "Description", length = 255)
    private String description;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "IsActive", nullable = false)
    private Boolean active;

    public Product() {
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getGemstone() {
        return gemstone;
    }

    public void setGemstone(String gemstone) {
        this.gemstone = gemstone;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

