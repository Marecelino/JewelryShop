package com.example.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Request body to create a new product")
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Schema(description = "Product name", example = "Classic Gold Ring")
    private String productName;

    @NotBlank(message = "Category is required")
    @Schema(description = "Product category", example = "Rings")
    private String category;

    @NotBlank(message = "Material is required")
    @Schema(description = "Primary material", example = "18K Gold")
    private String material;

    @Schema(description = "Gemstone (optional)", example = "Diamond")
    private String gemstone;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be zero or greater")
    @Schema(description = "Price in VND", example = "5500000")
    private BigDecimal price;

    @Schema(description = "Product description (optional)")
    private String description;

    public CreateProductRequest() {
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}