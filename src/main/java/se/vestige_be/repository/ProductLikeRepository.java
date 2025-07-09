package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.ProductLike;

import java.util.Optional;
import java.util.List;

public interface ProductLikeRepository extends JpaRepository<ProductLike, Long> {
    Optional<ProductLike> findByUserUserIdAndProductProductId(Long userId, Long productId);
    List<ProductLike> findByUserUserId(Long userId);
    Long countByProductProductId(Long productId);
    void deleteByUserUserIdAndProductProductId(Long userId, Long productId);
}
