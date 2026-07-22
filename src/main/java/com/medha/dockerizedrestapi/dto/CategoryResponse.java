package com.medha.dockerizedrestapi.dto;

/** Read model for a category, including a denormalized count of products for convenience. */
public record CategoryResponse(
        Long id,
        String name,
        String description,
        int productCount
) {
}
