package com.example.product.dto;

import java.util.List;

public class ProductPageResponse {
    private List<ProductResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private String sortBy;
    private String sortDir;
    private boolean hasNext;
    private boolean hasPrevious;

    public ProductPageResponse(
            List<ProductResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            String sortBy,
            String sortDir,
            boolean hasNext,
            boolean hasPrevious
    ) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.sortBy = sortBy;
        this.sortDir = sortDir;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
    }

    public List<ProductResponse> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public String getSortBy() {
        return sortBy;
    }

    public String getSortDir() {
        return sortDir;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public boolean isHasPrevious() {
        return hasPrevious;
    }
}
