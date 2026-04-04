package com.example.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.enabled=true",
                "security.jwt.secret=test-secret-key-for-gateway-jwt-signing-2026-very-long",
                "INTERNAL_SERVICE_TOKEN=test-internal-service-token"
        }
)
class RouteAuthorizationEvaluatorTest {

    @Autowired
    private RouteAuthorizationEvaluator evaluator;

    @Test
    void customerCanCreateOrderButCannotCreateProduct() {
        assertThat(evaluator.authorize("/api/v1/orders", HttpMethod.POST, "CUSTOMER", "u-1").allowed()).isTrue();
        assertThat(evaluator.authorize("/api/v1/products", HttpMethod.POST, "CUSTOMER", "u-1").allowed()).isFalse();
    }

    @Test
    void sellerCanCreateProductButCannotCreateOrder() {
        assertThat(evaluator.authorize("/api/v1/products", HttpMethod.POST, "SELLER", "u-2").allowed()).isTrue();
        assertThat(evaluator.authorize("/api/v1/orders", HttpMethod.POST, "SELLER", "u-2").allowed()).isFalse();
    }

    @Test
    void productManagementEndpointsAreRestrictedToSellerAndAdmin() {
        assertThat(evaluator.authorize("/api/v1/products/p-1", HttpMethod.PUT, "SELLER", "u-2").allowed()).isTrue();
        assertThat(evaluator.authorize("/api/v1/products/p-1", HttpMethod.PATCH, "ADMIN", "u-3").allowed()).isTrue();
        assertThat(evaluator.authorize("/api/v1/products/p-1", HttpMethod.DELETE, "CUSTOMER", "u-4").allowed()).isFalse();
    }

    @Test
    void adminCanAccessPaymentEndpoints() {
        assertThat(evaluator.authorize("/api/v1/payments/pay", HttpMethod.POST, "ADMIN", "u-3").allowed()).isTrue();
        assertThat(evaluator.authorize("/api/v1/payments/order/123", HttpMethod.GET, "ADMIN", "u-3").allowed()).isTrue();
    }

    @Test
    void nonAdminCannotAccessPaymentEndpoints() {
        assertThat(evaluator.authorize("/api/v1/payments/pay", HttpMethod.POST, "CUSTOMER", "u-4").allowed()).isFalse();
        assertThat(evaluator.authorize("/api/v1/payments/pay", HttpMethod.POST, "SELLER", "u-4").allowed()).isFalse();
    }

    @Test
    void onlyAdminCanReadOutboxRoute() {
        assertThat(evaluator.authorize("/api/v1/orders/outbox/pending", HttpMethod.GET, "ADMIN", "u-5").allowed()).isTrue();
        assertThat(evaluator.authorize("/api/v1/orders/outbox/pending", HttpMethod.GET, "CUSTOMER", "u-5").allowed()).isFalse();
        assertThat(evaluator.authorize("/api/v1/orders/outbox/pending", HttpMethod.GET, "SELLER", "u-5").allowed()).isFalse();
    }

    @Test
    void ownerCheckWorksForUserProfileRoute() {
        assertThat(evaluator.authorize("/api/v1/users/abc-1", HttpMethod.GET, "CUSTOMER", "abc-1").allowed()).isTrue();
        assertThat(evaluator.authorize("/api/v1/users/abc-1", HttpMethod.GET, "CUSTOMER", "abc-2").allowed()).isFalse();
        assertThat(evaluator.authorize("/api/v1/users/abc-1", HttpMethod.GET, "ADMIN", "admin-id").allowed()).isTrue();
    }

    @Test
    void apiVersionPrefixIsMandatory() {
        assertThat(evaluator.isApiPath("/api/users")).isTrue();
        assertThat(evaluator.isVersionedApiPath("/api/users")).isFalse();
        assertThat(evaluator.isVersionedApiPath("/api/v1/users")).isTrue();
    }
}
