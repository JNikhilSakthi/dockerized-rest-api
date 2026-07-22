package com.medha.dockerizedrestapi.service.impl;

import com.medha.dockerizedrestapi.domain.Category;
import com.medha.dockerizedrestapi.domain.Product;
import com.medha.dockerizedrestapi.dto.ProductRequest;
import com.medha.dockerizedrestapi.dto.ProductResponse;
import com.medha.dockerizedrestapi.exception.DuplicateResourceException;
import com.medha.dockerizedrestapi.exception.ResourceNotFoundException;
import com.medha.dockerizedrestapi.mapper.ProductMapper;
import com.medha.dockerizedrestapi.repository.CategoryRepository;
import com.medha.dockerizedrestapi.repository.ProductRepository;
import com.medha.dockerizedrestapi.service.ProductService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductRepository productRepository,
            CategoryRepository categoryRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
    }

    @Override
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySkuIgnoreCase(request.sku())) {
            throw new DuplicateResourceException(
                    "A product with sku '" + request.sku() + "' already exists");
        }
        Category category = findCategoryOrThrow(request.categoryId());

        Product product = new Product();
        applyRequest(product, request, category);

        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return productMapper.toResponse(findProductOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAll(Long categoryId) {
        List<Product> products = categoryId == null
                ? productRepository.findAll()
                : productRepository.findByCategoryId(categoryId);
        return products.stream().map(productMapper::toResponse).toList();
    }

    @Override
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findProductOrThrow(id);

        productRepository.findBySkuIgnoreCase(request.sku())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "A product with sku '" + request.sku() + "' already exists");
                });

        Category category = findCategoryOrThrow(request.categoryId());
        applyRequest(product, request, category);

        return productMapper.toResponse(product);
    }

    @Override
    public void delete(Long id) {
        Product product = findProductOrThrow(id);
        productRepository.delete(product);
    }

    private void applyRequest(Product product, ProductRequest request, Category category) {
        product.setName(request.name());
        product.setSku(request.sku());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setQuantity(request.quantity());
        product.setCategory(category);
    }

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Product", id));
    }

    private Category findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Category", categoryId));
    }
}
