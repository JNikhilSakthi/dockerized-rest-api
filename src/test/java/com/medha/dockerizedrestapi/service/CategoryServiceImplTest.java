package com.medha.dockerizedrestapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medha.dockerizedrestapi.domain.Category;
import com.medha.dockerizedrestapi.dto.CategoryRequest;
import com.medha.dockerizedrestapi.dto.CategoryResponse;
import com.medha.dockerizedrestapi.exception.DuplicateResourceException;
import com.medha.dockerizedrestapi.exception.ResourceNotFoundException;
import com.medha.dockerizedrestapi.mapper.CategoryMapper;
import com.medha.dockerizedrestapi.repository.CategoryRepository;
import com.medha.dockerizedrestapi.service.impl.CategoryServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryServiceImpl categoryService;

    private Category electronics;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(categoryRepository, new CategoryMapper());
        electronics = new Category("Electronics", "Gadgets and devices");
        electronics.setId(1L);
    }

    @Test
    void create_savesNewCategory_whenNameIsUnique() {
        CategoryRequest request = new CategoryRequest("Electronics", "Gadgets and devices");
        when(categoryRepository.existsByNameIgnoreCase("Electronics")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(electronics);

        CategoryResponse response = categoryService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Electronics");
        assertThat(response.productCount()).isZero();
    }

    @Test
    void create_throwsDuplicateResourceException_whenNameAlreadyExists() {
        CategoryRequest request = new CategoryRequest("Electronics", "Gadgets");
        when(categoryRepository.existsByNameIgnoreCase("Electronics")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Electronics");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void getById_throwsResourceNotFoundException_whenMissing() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_appliesNewValues_whenNameUnchanged() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
        when(categoryRepository.findByNameIgnoreCase("Electronics")).thenReturn(Optional.of(electronics));

        CategoryResponse response = categoryService.update(1L,
                new CategoryRequest("Electronics", "Updated description"));

        assertThat(response.description()).isEqualTo("Updated description");
    }

    @Test
    void update_throwsDuplicateResourceException_whenRenamingToAnotherExistingCategory() {
        Category books = new Category("Books", "Reading material");
        books.setId(2L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
        when(categoryRepository.findByNameIgnoreCase("Books")).thenReturn(Optional.of(books));

        assertThatThrownBy(() -> categoryService.update(1L, new CategoryRequest("Books", "desc")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void delete_removesCategory_whenItExists() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));

        categoryService.delete(1L);

        verify(categoryRepository).delete(electronics);
    }

    @Test
    void delete_throwsResourceNotFoundException_whenMissing() {
        when(categoryRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
