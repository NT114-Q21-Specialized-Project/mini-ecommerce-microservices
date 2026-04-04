package com.example.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public class ProductPatchRequest {

    private String name;

    @Positive(message = "price must be greater than 0")
    private Double price;

    @Min(value = 0, message = "stock must be greater than or equal to 0")
    private Integer stock;

    public String getName() {
        return name;
    }

    public Double getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }
}
