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

}
