package com.medha.dockerizedrestapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medha.dockerizedrestapi.domain.Category;
import com.medha.dockerizedrestapi.domain.Product;
import com.medha.dockerizedrestapi.dto.ProductRequest;
import com.medha.dockerizedrestapi.dto.ProductResponse;
import com.medha.dockerizedrestapi.exception.DuplicateResourceException;
import com.medha.dockerizedrestapi.exception.ResourceNotFoundException;
import com.medha.dockerizedrestapi.mapper.ProductMapper;
import com.medha.dockerizedrestapi.repository.CategoryRepository;
import com.medha.dockerizedrestapi.repository.ProductRepository;
import com.medha.dockerizedrestapi.service.impl.ProductServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private ProductServiceImpl productService;

    private Category electronics;
    private Product mouse;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productRepository, categoryRepository, new ProductMapper());

        electronics = new Category("Electronics", "Gadgets");
        electronics.setId(1L);

        mouse = new Product();
        mouse.setId(10L);
        mouse.setName("Wireless Mouse");
        mouse.setSku("ELEC-001");
        mouse.setPrice(new BigDecimal("19.99"));
        mouse.setQuantity(150);
        mouse.setCategory(electronics);
    }

    @Test
    void create_savesProduct_whenSkuIsUniqueAndCategoryExists() {
        ProductRequest request = new ProductRequest("Wireless Mouse", "ELEC-001", "desc",
                new BigDecimal("19.99"), 150, 1L);

        when(productRepository.existsBySkuIgnoreCase("ELEC-001")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
        when(productRepository.save(any(Product.class))).thenReturn(mouse);

        ProductResponse response = productService.create(request);

        assertThat(response.sku()).isEqualTo("ELEC-001");
        assertThat(response.categoryId()).isEqualTo(1L);
    }

    @Test
    void create_throwsDuplicateResourceException_whenSkuAlreadyExists() {
        ProductRequest request = new ProductRequest("Wireless Mouse", "ELEC-001", "desc",
                new BigDecimal("19.99"), 150, 1L);
        when(productRepository.existsBySkuIgnoreCase("ELEC-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(categoryRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void create_throwsResourceNotFoundException_whenCategoryMissing() {
        ProductRequest request = new ProductRequest("Wireless Mouse", "ELEC-001", "desc",
                new BigDecimal("19.99"), 150, 99L);
        when(productRepository.existsBySkuIgnoreCase("ELEC-001")).thenReturn(false);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
    }

    @Test
    void getAll_filtersByCategory_whenCategoryIdProvided() {
        when(productRepository.findByCategoryId(1L)).thenReturn(List.of(mouse));

        List<ProductResponse> results = productService.getAll(1L);

        assertThat(results).hasSize(1);
        verify(productRepository, never()).findAll();
    }

    @Test
    void getAll_returnsEverything_whenCategoryIdIsNull() {
        when(productRepository.findAll()).thenReturn(List.of(mouse));

        List<ProductResponse> results = productService.getAll(null);

        assertThat(results).hasSize(1);
    }

    @Test
    void delete_throwsResourceNotFoundException_whenProductMissing() {
        when(productRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
