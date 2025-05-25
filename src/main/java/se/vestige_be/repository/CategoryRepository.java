package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category,Integer> {
    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    List<Category> findByParentCategory_CategoryId(Integer parentCategoryId);

    List<Category> findByParentCategoryIsNull();


}
