package com.example.product;

import com.example.product.model.Product;
import com.example.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductCrudIntegrationTest {

    private static final String SELLER_ROLE = "SELLER";
    private static final String CUSTOMER_ROLE = "CUSTOMER";
    private static final String ACTOR_ID = "3df3f75a-b388-4e59-ad55-df62cdef7f83";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void putReplacesExistingProduct() throws Exception {
        Product product = saveProduct("Original Product", 10.0, 4);

        mockMvc.perform(put("/products/{id}", product.getId())
                        .header("X-User-Id", ACTOR_ID)
                        .header("X-User-Role", SELLER_ROLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Product",
                                  "price": 12.75,
                                  "stock": 9
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.name").value("Updated Product"))
                .andExpect(jsonPath("$.price").value(12.75))
                .andExpect(jsonPath("$.stock").value(9));

        Product stored = repository.findById(product.getId()).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Updated Product");
        assertThat(stored.getPrice()).isEqualTo(12.75);
        assertThat(stored.getStock()).isEqualTo(9);
    }

    @Test
    void patchUpdatesOnlyProvidedFields() throws Exception {
        Product product = saveProduct("Patchable Product", 19.99, 7);

        mockMvc.perform(patch("/products/{id}", product.getId())
                        .header("X-User-Id", ACTOR_ID)
                        .header("X-User-Role", SELLER_ROLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "price": 25.5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.name").value("Patchable Product"))
                .andExpect(jsonPath("$.price").value(25.5))
                .andExpect(jsonPath("$.stock").value(7));

        Product stored = repository.findById(product.getId()).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Patchable Product");
        assertThat(stored.getPrice()).isEqualTo(25.5);
        assertThat(stored.getStock()).isEqualTo(7);
    }

    @Test
    void patchRequiresAtLeastOneField() throws Exception {
        Product product = saveProduct("Empty Patch", 8.5, 3);

        mockMvc.perform(patch("/products/{id}", product.getId())
                        .header("X-User-Id", ACTOR_ID)
                        .header("X-User-Role", SELLER_ROLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PATCH_REQUEST"));
    }

    @Test
    void deleteRemovesExistingProduct() throws Exception {
        Product product = saveProduct("Disposable Product", 14.0, 2);

        mockMvc.perform(delete("/products/{id}", product.getId())
                        .header("X-User-Id", ACTOR_ID)
                        .header("X-User-Role", SELLER_ROLE))
                .andExpect(status().isNoContent());

        assertThat(repository.findById(product.getId())).isEmpty();

        mockMvc.perform(get("/products/{id}", product.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void customerCannotDeleteProduct() throws Exception {
        Product product = saveProduct("Protected Product", 11.0, 5);

        mockMvc.perform(delete("/products/{id}", product.getId())
                        .header("X-User-Id", ACTOR_ID)
                        .header("X-User-Role", CUSTOMER_ROLE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROLE_NOT_ALLOWED"));

        assertThat(repository.findById(product.getId())).isPresent();
    }

    private Product saveProduct(String name, double price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(price);
        product.setStock(stock);
        return repository.save(product);
    }
}
