package com.medha.dockerizedrestapi.mapper;

import com.medha.dockerizedrestapi.domain.Category;
import com.medha.dockerizedrestapi.dto.CategoryResponse;
import org.springframework.stereotype.Component;

/** Hand-rolled mapper between {@link Category} and its DTOs (kept dependency-free on purpose). */
@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        int productCount = category.getProducts() == null ? 0 : category.getProducts().size();
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                productCount);
    }
}
