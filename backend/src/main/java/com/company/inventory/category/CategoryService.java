package com.company.inventory.category;

import com.company.inventory.common.error.ResourceNotFoundException;
import com.company.inventory.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<CategoryDto> search(String search, Boolean active, int page, int size) {
        return categoryRepository
                .search(normalize(search), active, PageRequest.of(page, size))
                .map(CategoryDto::from);
    }

    @Transactional(readOnly = true)
    public CategoryDto get(Long id) {
        return CategoryDto.from(find(id));
    }

    @Transactional
    public CategoryDto create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new com.company.inventory.common.error.BusinessRuleException(
                    "CATEGORY_EXISTS", "A category with this name already exists.");
        }
        Category category = new Category();
        category.setName(request.name().trim());
        category.setDescription(orEmpty(request.description()));
        category.setActive(true);
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        return CategoryDto.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        Category category = find(id);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new com.company.inventory.common.error.BusinessRuleException(
                    "CATEGORY_EXISTS", "A category with this name already exists.");
        }
        category.setName(request.name().trim());
        category.setDescription(orEmpty(request.description()));
        category.setUpdatedAt(LocalDateTime.now());
        return CategoryDto.from(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        long inUse = productRepository.countByCategoryId(id);
        if (inUse > 0) {
            throw new com.company.inventory.common.error.BusinessRuleException(
                    "CATEGORY_IN_USE",
                    "Category is used by " + inUse + " product(s). Deactivate it instead of deleting.");
        }
        categoryRepository.delete(category);
    }

    private Category find(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private String normalize(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
