package com.example.product.dto.request;

import jakarta.validation.constraints.Min;

public class ProductPageRequest {

    @Min(value = 0, message = "Page must be greater than or equal to 0")
    private int page = 0;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}