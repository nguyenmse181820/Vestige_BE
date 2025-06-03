package se.vestige_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import se.vestige_be.pojo.Product;
import se.vestige_be.pojo.enums.ProductStatus;

public interface ProductRepository extends JpaRepository<Product,Long>, JpaSpecificationExecutor<Product> {
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findBySellerUserId(Long sellerId, Pageable pageable);

    Long countBySellerUserId(Long sellerId);

    Long countBySellerUserIdAndStatus(Long sellerId, ProductStatus status);

    boolean existsBySellerUserIdAndTitle(Long sellerId, String title);

    Page<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status, Pageable pageable);
}
