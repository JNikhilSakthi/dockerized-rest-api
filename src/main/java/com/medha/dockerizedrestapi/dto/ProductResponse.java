package com.medha.dockerizedrestapi.dto;

import com.medha.dockerizedrestapi.domain.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Read model for a product, with a flattened category reference. */
public record ProductResponse(
        Long id,
        String name,
        String sku,
        String description,
        BigDecimal price,
        Integer quantity,
        ProductStatus status,
        Long categoryId,
        String categoryName,
        Instant createdAt,
        Instant updatedAt
) {
}
