package com.medha.dockerizedrestapi.service;

import com.medha.dockerizedrestapi.dto.ProductRequest;
import com.medha.dockerizedrestapi.dto.ProductResponse;
import java.util.List;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse getById(Long id);

    List<ProductResponse> getAll(Long categoryId);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);
}
