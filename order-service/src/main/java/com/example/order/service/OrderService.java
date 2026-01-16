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

    public Order createOrder(UUID userId, Double totalAmount) {

        // 1. Validate user by calling user-service
        String userServiceUrl = "http://user-service:8080/users/" + userId;

        try {
            restTemplate.getForObject(userServiceUrl, String.class);
        } catch (HttpClientErrorException.NotFound e) {
            // user-service trả 404
            throw new IllegalArgumentException("User not found");
        } catch (HttpClientErrorException e) {
            // các lỗi HTTP khác
            throw new RuntimeException("Failed to validate user");
        }

        // 2. Create order
        Order order = new Order();
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus("CREATED");

        return repository.save(order);
    }
}
