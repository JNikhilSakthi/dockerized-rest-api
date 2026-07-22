package com.medha.dockerizedrestapi.mapper;

import com.medha.dockerizedrestapi.domain.Product;
import com.medha.dockerizedrestapi.dto.ProductResponse;
import org.springframework.stereotype.Component;

/** Hand-rolled mapper between {@link Product} and its DTOs. */
@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getPrice(),
                product.getQuantity(),
                product.getStatus(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
