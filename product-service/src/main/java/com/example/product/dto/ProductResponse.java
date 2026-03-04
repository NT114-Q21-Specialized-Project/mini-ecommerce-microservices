package com.example.product.dto;

import java.time.Instant;
import java.util.UUID;

public class ProductResponse {
    private UUID id;
    private String name;
    private Double price;
    private Integer stock;
    private Instant createdAt;

    public ProductResponse(UUID id, String name, Double price, Integer stock, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Double getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
