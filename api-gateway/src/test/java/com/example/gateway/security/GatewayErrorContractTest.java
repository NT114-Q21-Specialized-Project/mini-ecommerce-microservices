package com.example.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.enabled=true",
                "security.jwt.secret=test-secret-key-for-gateway-jwt-signing-2026-very-long"
        }
)
@AutoConfigureWebTestClient
class GatewayErrorContractTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unversionedApiPathReturnsStandardized404Error() throws Exception {
        EntityExchangeResult<byte[]> result = webTestClient.get()
                .uri("/api/users/health")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody()
                .returnResult();

        JsonNode body = parseBody(result);
        String responseCorrelationId = result.getResponseHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertThat(body.get("code").asText()).isEqualTo("API_VERSION_REQUIRED");
        assertThat(body.get("message").asText()).contains("/api/v1");
        assertThat(body.get("path").asText()).isEqualTo("/api/users/health");
        assertThat(responseCorrelationId).isNotBlank();
        assertThat(body.get("correlationId").asText()).isEqualTo(responseCorrelationId);
    }

    @Test
    void missingBearerTokenReturnsStandardized401Error() throws Exception {
        String callerCorrelationId = "cid-missing-token-001";

        EntityExchangeResult<byte[]> result = webTestClient.get()
                .uri("/api/v1/users")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, callerCorrelationId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .returnResult();

        JsonNode body = parseBody(result);
        String responseCorrelationId = result.getResponseHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertThat(body.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message").asText()).contains("Authorization");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/users");
        assertThat(responseCorrelationId).isEqualTo(callerCorrelationId);
        assertThat(body.get("correlationId").asText()).isEqualTo(callerCorrelationId);
    }

    @Test
    void malformedTokenReturnsStandardized401Error() throws Exception {
        EntityExchangeResult<byte[]> result = webTestClient.get()
                .uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .returnResult();

        JsonNode body = parseBody(result);
        String responseCorrelationId = result.getResponseHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertThat(body.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message").asText()).contains("Invalid");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/orders");
        assertThat(responseCorrelationId).isNotBlank();
        assertThat(body.get("correlationId").asText()).isEqualTo(responseCorrelationId);
    }

    private JsonNode parseBody(EntityExchangeResult<byte[]> result) throws Exception {
        byte[] bodyBytes = result.getResponseBody();
        assertThat(bodyBytes).isNotNull();
        String payload = new String(bodyBytes, StandardCharsets.UTF_8);
        return objectMapper.readTree(payload);
    }
}
