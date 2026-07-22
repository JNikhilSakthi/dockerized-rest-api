package com.medha.dockerizedrestapi.service;

import com.medha.dockerizedrestapi.dto.CategoryRequest;
import com.medha.dockerizedrestapi.dto.CategoryResponse;
import java.util.List;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    CategoryResponse getById(Long id);

    List<CategoryResponse> getAll();

    CategoryResponse update(Long id, CategoryRequest request);

    void delete(Long id);
}
