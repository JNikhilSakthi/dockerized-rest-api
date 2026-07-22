package com.medha.dockerizedrestapi.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medha.dockerizedrestapi.dto.ProductRequest;
import com.medha.dockerizedrestapi.dto.ProductResponse;
import com.medha.dockerizedrestapi.domain.ProductStatus;
import com.medha.dockerizedrestapi.exception.DuplicateResourceException;
import com.medha.dockerizedrestapi.exception.ResourceNotFoundException;
import com.medha.dockerizedrestapi.service.ProductService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private ProductResponse sampleResponse() {
        return new ProductResponse(10L, "Wireless Mouse", "ELEC-001", "desc",
                new BigDecimal("19.99"), 150, ProductStatus.ACTIVE, 1L, "Electronics",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void create_returns201_withLocationHeader_whenPayloadIsValid() throws Exception {
        ProductRequest request = new ProductRequest("Wireless Mouse", "ELEC-001", "desc",
                new BigDecimal("19.99"), 150, 1L);
        when(productService.create(any(ProductRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("ELEC-001"))
                .andExpect(jsonPath("$.categoryName").value("Electronics"));
    }

    @Test
    void create_returns400_whenPriceIsNegative() throws Exception {
        ProductRequest invalid = new ProductRequest("Mouse", "ELEC-001", "desc",
                new BigDecimal("-5.00"), 150, 1L);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.price").exists());
    }

    @Test
    void create_returns409_whenSkuAlreadyExists() throws Exception {
        ProductRequest request = new ProductRequest("Wireless Mouse", "ELEC-001", "desc",
                new BigDecimal("19.99"), 150, 1L);
        when(productService.create(any(ProductRequest.class)))
                .thenThrow(new DuplicateResourceException("A product with sku 'ELEC-001' already exists"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("ELEC-001")));
    }

    @Test
    void getById_returns404_whenProductMissing() throws Exception {
        when(productService.getById(anyLong()))
                .thenThrow(ResourceNotFoundException.forEntity("Product", 999L));

        mockMvc.perform(get("/api/v1/products/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("999")));
    }

    @Test
    void getAll_returnsList() throws Exception {
        when(productService.getAll(null)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("ELEC-001"));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{id}", 10L))
                .andExpect(status().isNoContent());
    }
}
