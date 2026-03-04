package com.example.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ProductCreateRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "price is required")
    @Positive(message = "price must be greater than 0")
    private Double price;

    @NotNull(message = "stock is required")
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
}
