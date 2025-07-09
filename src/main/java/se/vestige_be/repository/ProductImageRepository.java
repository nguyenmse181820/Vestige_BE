package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.ProductImage;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Integer>, ProductImageRepositoryCustom {
    List<ProductImage> findByProductProductIdAndActiveTrueOrderByDisplayOrderAsc(Long productId);
}
