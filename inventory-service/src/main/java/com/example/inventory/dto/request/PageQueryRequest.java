package com.example.inventory.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class PageQueryRequest {

    @Min(value = 0, message = "Page number must be greater than or equal to 0")
    private int pageNumber = 0;

    @Min(value = 1, message = "Page size must be greater than 0")
    @Max(value = 27, message = "Page size must not exceed 27")
    private int size = 4;

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
