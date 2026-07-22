package com.medha.dockerizedrestapi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medha.dockerizedrestapi.dto.CategoryRequest;
import com.medha.dockerizedrestapi.dto.ProductRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end test that boots the full Spring context against a real, disposable MySQL instance
 * launched via Testcontainers - the same MySQL 8.0 image used by {@code docker-compose.yml} -
 * rather than an in-memory substitute, so the SQL dialect and schema generation are verified
 * against the exact database engine the containerized app runs against in production.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProductApiIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("catalog_db_it")
            .withUsername("catalog_user")
            .withPassword("catalog_pass");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // Skip the demo seed data for this test's isolated schema.
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void createCategoryThenProduct_persistsAndLinksThemAcrossRealMySql() throws Exception {
        CategoryRequest categoryRequest = new CategoryRequest("Toys", "Toys and games");

        String categoryJson = mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(categoryRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long categoryId = jsonMapper.readTree(categoryJson).get("id").asLong();

        ProductRequest productRequest = new ProductRequest("Building Blocks", "TOY-100",
                "120-piece wooden block set", new BigDecimal("24.99"), 200, categoryId);

        String productJson = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryName").value("Toys"))
                .andReturn().getResponse().getContentAsString();

        Long productId = jsonMapper.readTree(productJson).get("id").asLong();

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("TOY-100"));

        mockMvc.perform(get("/api/v1/categories/{id}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCount").value(1));

        assertThat(productId).isNotNull();
    }
}
