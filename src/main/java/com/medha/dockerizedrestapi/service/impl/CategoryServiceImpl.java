package com.medha.dockerizedrestapi.service.impl;

import com.medha.dockerizedrestapi.domain.Category;
import com.medha.dockerizedrestapi.dto.CategoryRequest;
import com.medha.dockerizedrestapi.dto.CategoryResponse;
import com.medha.dockerizedrestapi.exception.DuplicateResourceException;
import com.medha.dockerizedrestapi.exception.ResourceNotFoundException;
import com.medha.dockerizedrestapi.mapper.CategoryMapper;
import com.medha.dockerizedrestapi.repository.CategoryRepository;
import com.medha.dockerizedrestapi.service.CategoryService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException(
                    "A category named '" + request.name() + "' already exists");
        }
        Category category = new Category(request.name(), request.description());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return categoryMapper.toResponse(findCategoryOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findCategoryOrThrow(id);

        categoryRepository.findByNameIgnoreCase(request.name())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "A category named '" + request.name() + "' already exists");
                });

        category.setName(request.name());
        category.setDescription(request.description());
        return categoryMapper.toResponse(category);
    }

    @Override
    public void delete(Long id) {
        Category category = findCategoryOrThrow(id);
        categoryRepository.delete(category);
    }

    private Category findCategoryOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Category", id));
    }
}
