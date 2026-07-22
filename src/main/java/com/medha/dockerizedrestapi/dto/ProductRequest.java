package com.medha.dockerizedrestapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Payload for creating or updating a product. */
public record ProductRequest(

        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @NotBlank(message = "sku is required")
        @Size(max = 40, message = "sku must be at most 40 characters")
        String sku,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "price must not be negative")
        @Digits(integer = 8, fraction = 2, message = "price may have at most 2 decimal places")
        BigDecimal price,

        @NotNull(message = "quantity is required")
        @Min(value = 0, message = "quantity must not be negative")
        Integer quantity,

        @NotNull(message = "categoryId is required")
        Long categoryId
) {
}
