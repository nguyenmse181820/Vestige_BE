package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category,Long> {
    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    List<Category> findByParentCategory_CategoryId(Long parentCategoryId);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.subcategories WHERE c.parentCategory IS NULL")
    List<Category> findByParentCategoryIsNull();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.subcategories LEFT JOIN FETCH c.parentCategory WHERE c.categoryId = :id")
    Optional<Category> findByIdWithSubcategories(@Param("id") Long id);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parentCategory WHERE c.categoryId = :id")
    Optional<Category> findByIdWithParent(@Param("id") Long id);

    /**
     * Get all category IDs including the specified category and all its descendants (subcategories)
     * This is used for hierarchical filtering where selecting a parent category should include all child categories
     */
    @Query(value = """
        WITH RECURSIVE category_tree AS (
            SELECT category_id, parent_category_id, name, 0 as level
            FROM categories 
            WHERE category_id = :categoryId
            
            UNION ALL
            
            SELECT c.category_id, c.parent_category_id, c.name, ct.level + 1
            FROM categories c
            INNER JOIN category_tree ct ON c.parent_category_id = ct.category_id
        )
        SELECT category_id FROM category_tree
        """, nativeQuery = true)
    List<Long> findCategoryIdWithAllSubcategoryIds(@Param("categoryId") Long categoryId);

    /**
     * Check if a category has any subcategories
     */
    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.parentCategory.categoryId = :categoryId")
    boolean hasSubcategories(@Param("categoryId") Long categoryId);
}
