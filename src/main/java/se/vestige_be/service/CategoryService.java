package se.vestige_be.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.response.CategoryResponse;
import se.vestige_be.pojo.Category;
import se.vestige_be.repository.CategoryRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> findAll() {
        List<Category> categories = categoryRepository.findAll();
        return CategoryResponse.fromEntityList(categories);
    }

    public Optional<Category> findById(Long id) {
        return categoryRepository.findById(id);
    }

    public List<Category> findByParentCategory(Long parentCategoryId) {
        return categoryRepository.findByParentCategory_CategoryId(parentCategoryId);
    }

    @Transactional
    public Category createCategory(String name, String description, Long parentCategoryId) {
        Category parentCategory = null;
        if(parentCategoryId != null) {
            parentCategory = findById(parentCategoryId).orElseThrow(() -> new EntityNotFoundException("Parent category not found with id: " + parentCategoryId));
        }
        if (categoryRepository.existsByName(name)) {
            throw new RuntimeException("Category with name '" + name + "' already exists");
        }
        Category category = Category.builder()
                .name(name)
                .description(description)
                .parentCategory(parentCategory)
                .createdAt(LocalDateTime.now())
                .build();
        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(Long id, String name, String description, Long parentCategoryId) {
        Category existingCategory = findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category with id '" + id + "' not found"));

        if(name != null) {
            if (!name.equals(existingCategory.getName()) && categoryRepository.existsByName(name)) {
                throw new RuntimeException("Category with name '" + name + "' already exists");
            }
            existingCategory.setName(name);
        }

        if(description != null) {
            existingCategory.setDescription(description);
        }

        if(parentCategoryId != null) {
            Category parentCategory = findById(parentCategoryId)
                    .orElseThrow(() -> new EntityNotFoundException("Parent category with id '" + parentCategoryId + "' not found"));
            if (isCircularReference(id, parentCategoryId)) {
                throw new RuntimeException("Cannot set parent category: would create circular reference");
            }
            existingCategory.setParentCategory(parentCategory);
        }

        return categoryRepository.save(existingCategory);
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category with id '" + categoryId + "' not found"));

        List<Category> subcategories = findByParentCategory(categoryId);
        if (!subcategories.isEmpty()) {
            throw new RuntimeException("Cannot delete category '" + category.getName() +
                    "': has " + subcategories.size() + " subcategories. Delete subcategories first.");
        }
        if (!category.getProducts().isEmpty()) {
            throw new RuntimeException("Cannot delete category '" + category.getName() +
                    "': has " + category.getProducts().size() + " associated products. " +
                    "Move or delete products first.");
        }

        categoryRepository.deleteById(categoryId);
    }

    private boolean isCircularReference(Long categoryId, Long parentCategoryId) {
        if (categoryId.equals(parentCategoryId)) {
            return true;
        }

        Category parent = findById(parentCategoryId).orElse(null);
        while (parent != null && parent.getParentCategory() != null) {
            if (parent.getParentCategory().getCategoryId().equals(categoryId)) {
                return true;
            }
            parent = parent.getParentCategory();
        }

        return false;
    }

}
