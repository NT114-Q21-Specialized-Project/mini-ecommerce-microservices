package com.example.order.service;

import com.example.order.model.Order;
import com.example.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final RestTemplate restTemplate;
    private final String userServiceBaseUrl;
    private final String productServiceBaseUrl;

    public OrderService(
            OrderRepository repository,
            RestTemplate restTemplate,
            @Value("${clients.user-service.base-url}") String userServiceBaseUrl,
            @Value("${clients.product-service.base-url}") String productServiceBaseUrl
    ) {
        this.repository = repository;
        this.restTemplate = restTemplate;
        this.userServiceBaseUrl = userServiceBaseUrl.replaceAll("/+$", "");
        this.productServiceBaseUrl = productServiceBaseUrl.replaceAll("/+$", "");
    }

    public Order createOrder(
            UUID userId,
            UUID productId,
            Integer quantity,
            Double totalAmount
    ) {
        validateOrderPayload(quantity, totalAmount);
        validateUser(userId);

        ProductResponse product = getProduct(productId);
        if (product.getStock() == null || product.getStock() < quantity) {
            throw new IllegalArgumentException("Not enough stock");
        }

        decreaseProductStock(productId, quantity);

        Order order = new Order();
        order.setUserId(userId);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setTotalAmount(totalAmount);
        order.setStatus("CREATED");

        return repository.save(order);
    }

    private void validateOrderPayload(Integer quantity, Double totalAmount) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        if (totalAmount == null || totalAmount <= 0) {
            throw new IllegalArgumentException("Total amount must be greater than 0");
        }
    }

    private void validateUser(UUID userId) {
        String url = userServiceBaseUrl + "/users/" + userId + "/validate";
        UserValidationResponse response;

        try {
            response = restTemplate.getForObject(url, UserValidationResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("User not found");
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("User-service returned error: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("User service unavailable at " + url);
        } catch (RestClientException e) {
            throw new IllegalStateException("Error communicating with user-service: " + e.getMessage());
        }

        if (response == null || !Boolean.TRUE.equals(response.getValid()) || !Boolean.TRUE.equals(response.getIsActive())) {
            throw new IllegalArgumentException("User not found or inactive");
        }
    }

    private ProductResponse getProduct(UUID productId) {
        String productUrl = productServiceBaseUrl + "/products/" + productId;

        try {
            ProductResponse response = restTemplate.getForObject(productUrl, ProductResponse.class);
            if (response == null) {
                throw new IllegalStateException("Invalid response from product-service");
            }
            return response;
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Product not found");
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Product-service returned error: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Product service unavailable at " + productUrl);
        } catch (RestClientException e) {
            throw new IllegalStateException("Error communicating with product-service: " + e.getMessage());
        }
    }

    private void decreaseProductStock(UUID productId, Integer quantity) {
        String decreaseStockUrl = productServiceBaseUrl + "/products/" + productId
                + "/decrease-stock?quantity=" + quantity;

        try {
            restTemplate.postForEntity(decreaseStockUrl, null, Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Product not found");
        } catch (HttpClientErrorException.BadRequest e) {
            String message = extractErrorMessage(e, "Unable to decrease stock");
            throw new IllegalArgumentException(message);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Product-service returned error: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Product service unavailable at " + decreaseStockUrl);
        } catch (RestClientException e) {
            throw new IllegalStateException("Error communicating with product-service: " + e.getMessage());
        }
    }

    private String extractErrorMessage(HttpClientErrorException exception, String fallback) {
        String responseBody = exception.getResponseBodyAsString();
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return fallback;
        }
        return responseBody;
    }

    private static class ProductResponse {
        private Integer stock;

        public Integer getStock() {
            return stock;
        }

        public void setStock(Integer stock) {
            this.stock = stock;
        }
    }

    private static class UserValidationResponse {
        private Boolean valid;
        private Boolean isActive;

        public Boolean getValid() {
            return valid;
        }

        public void setValid(Boolean valid) {
            this.valid = valid;
        }

        public Boolean getIsActive() {
            return isActive;
        }

        public void setIsActive(Boolean active) {
            isActive = active;
        }
    }
}
