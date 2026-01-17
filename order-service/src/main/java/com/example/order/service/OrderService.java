package com.example.order.service;

import com.example.order.model.Order;
import com.example.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final RestTemplate restTemplate = new RestTemplate();

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order createOrder(
            UUID userId,
            UUID productId,
            Integer quantity,
            Double totalAmount
    ) {

        // 1. Validate user
        try {
            restTemplate.getForObject(
                    "http://user-service:8080/users/" + userId,
                    String.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("User not found");
        }

        // 2. Validate product
        String productUrl = "http://product-service:8080/products/" + productId;
        ProductResponse product;

        try {
            product = restTemplate.getForObject(productUrl, ProductResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Product not found");
        }

        // 3. Check stock
        if (product.getStock() < quantity) {
            throw new IllegalArgumentException("Not enough stock");
        }

        // 4. Decrease stock
        restTemplate.postForObject(
                "http://product-service:8080/products/" + productId +
                        "/decrease-stock?quantity=" + quantity,
                null,
                Void.class
        );

        // 5. Save order
        Order order = new Order();
        order.setUserId(userId);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setTotalAmount(totalAmount);
        order.setStatus("CREATED");

        return repository.save(order);
    }

    // DTO nội bộ để map response product
    private static class ProductResponse {
        private Integer stock;

        public Integer getStock() {
            return stock;
        }

        public void setStock(Integer stock) {
            this.stock = stock;
        }
    }
}
