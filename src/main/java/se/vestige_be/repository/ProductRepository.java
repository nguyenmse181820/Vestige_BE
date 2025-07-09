package se.vestige_be.repository;

import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.Product;
import se.vestige_be.pojo.enums.ProductStatus;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product,Long>, JpaSpecificationExecutor<Product> {
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findBySellerUserId(Long sellerId, Pageable pageable);

    Long countBySellerUserId(Long sellerId);

    Long countBySellerUserIdAndStatus(Long sellerId, ProductStatus status);

    boolean existsBySellerUserIdAndTitle(Long sellerId, String title);

    Page<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status, Pageable pageable);

    Optional<Product> findBySellerUserIdAndTitle(Long sellerId, String title);

    boolean existsBySellerUserIdAndTitleAndProductIdNot(Long sellerId, @Size(min = 5, max = 100, message = "Title must be between 5 and 100 characters") String title, Long productId);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.brand LEFT JOIN FETCH p.seller WHERE p.productId = :id")
    Optional<Product> findByIdWithRelations(@Param("id") Long id);

    // Method to load products with images (separate from other collections to avoid MultipleBagFetchException)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.images WHERE p.productId IN :productIds")
    List<Product> findByProductIdInWithImages(@Param("productIds") List<Long> productIds);
    
    // Find product by slug
    Optional<Product> findBySlug(String slug);
    
    // Check if slug exists (for uniqueness validation)
    boolean existsBySlug(String slug);
      // Find by slug with all relations loaded
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.brand LEFT JOIN FETCH p.seller WHERE p.slug = :slug")
    Optional<Product> findBySlugWithRelations(@Param("slug") String slug);    // Check if multiple sellers have products with slugs starting with the base slug
    @Query("SELECT CASE WHEN COUNT(DISTINCT p.seller.userId) > 1 THEN true ELSE false END " +
           "FROM Product p WHERE p.slug LIKE CONCAT(:baseSlug, '%')")
    boolean hasMultipleSellersForSlug(@Param("baseSlug") String baseSlug);
}
