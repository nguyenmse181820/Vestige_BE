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
    private final CategoryRepository categoryRepository;    public List<CategoryResponse> findAll() {
        List<Category> categories = categoryRepository.findByParentCategoryIsNull();
        return CategoryResponse.fromEntityList(categories);
    }

    public Optional<Category> findById(Long id) {
        return categoryRepository.findByIdWithSubcategories(id);
    }

    public Optional<Category> findByIdBasic(Long id) {
        return categoryRepository.findById(id);
    }

    public List<Category> findByParentCategory(Long parentCategoryId) {
        return categoryRepository.findByParentCategory_CategoryId(parentCategoryId);
    }    @Transactional
    public CategoryResponse createCategory(String name, String description, Long parentCategoryId) {
        Category parentCategory = null;
        if(parentCategoryId != null) {
            parentCategory = findByIdBasic(parentCategoryId).orElseThrow(() -> new EntityNotFoundException("Parent category not found with id: " + parentCategoryId));
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
        
        Category savedCategory = categoryRepository.save(category);
        
        // Reload the saved category with parent data to avoid lazy loading issues
        if (savedCategory.getCategoryId() != null) {
            savedCategory = categoryRepository.findByIdWithParent(savedCategory.getCategoryId())
                    .orElse(savedCategory);
        }
        
        // Convert to response to avoid lazy loading issues
        return CategoryResponse.fromEntity(savedCategory, 0);
    }    @Transactional
    public CategoryResponse updateCategory(Long id, String name, String description, Long parentCategoryId) {
        Category existingCategory = findByIdBasic(id)
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
            Category parentCategory = findByIdBasic(parentCategoryId)
                    .orElseThrow(() -> new EntityNotFoundException("Parent category with id '" + parentCategoryId + "' not found"));
            if (isCircularReference(id, parentCategoryId)) {
                throw new RuntimeException("Cannot set parent category: would create circular reference");
            }
            existingCategory.setParentCategory(parentCategory);
        }

        Category savedCategory = categoryRepository.save(existingCategory);
        
        // Reload the saved category with parent data to avoid lazy loading issues
        if (savedCategory.getCategoryId() != null) {
            savedCategory = categoryRepository.findByIdWithParent(savedCategory.getCategoryId())
                    .orElse(savedCategory);
        }
        
        // Convert to response to avoid lazy loading issues
        return CategoryResponse.fromEntity(savedCategory, 0);
    }@Transactional
    public void deleteCategory(Long categoryId) {
        Category category = findByIdWithProducts(categoryId)
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
    }    @Transactional(readOnly = true)
    public Optional<Category> findByIdWithProducts(Long id) {
        return categoryRepository.findByIdWithSubcategories(id);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category = findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category with id '" + id + "' not found"));
        return CategoryResponse.fromEntity(category, 0);
    }    private boolean isCircularReference(Long categoryId, Long parentCategoryId) {
        if (categoryId.equals(parentCategoryId)) {
            return true;
        }

        Category parent = categoryRepository.findByIdWithParent(parentCategoryId).orElse(null);
        while (parent != null && parent.getParentCategory() != null) {
            if (parent.getParentCategory().getCategoryId().equals(categoryId)) {
                return true;
            }
            // For the next iteration, we need to fetch the parent's parent with its parent data too
            parent = categoryRepository.findByIdWithParent(parent.getParentCategory().getCategoryId()).orElse(null);
        }

        return false;
    }

    /**
     * Get all category IDs including the specified category and all its descendants
     * This is useful for hierarchical filtering where selecting a parent category 
     * should include products from all child categories
     */
    @Transactional(readOnly = true)
    public List<Long> getCategoryWithAllSubcategoryIds(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        
        return categoryRepository.findCategoryIdWithAllSubcategoryIds(categoryId);
    }
    
    /**
     * Check if a category has subcategories
     * This helps the frontend determine if a category can be expanded
     */
    @Transactional(readOnly = true)
    public boolean hasSubcategories(Long categoryId) {
        if (categoryId == null) {
            return false;
        }
        
        return categoryRepository.hasSubcategories(categoryId);
    }
      /**
     * Get category tree for display purposes
     * Returns the category with all its subcategories loaded
     */
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryTree(Long categoryId) {
        Category category = categoryRepository.findByIdWithSubcategories(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + categoryId));
        
        return CategoryResponse.fromEntity(category, 0);
    }

}
