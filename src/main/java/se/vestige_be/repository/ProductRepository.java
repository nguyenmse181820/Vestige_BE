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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

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
    
    // Add method for finding last product listing date for a user
    @Query("SELECT MAX(p.createdAt) FROM Product p WHERE p.seller.userId = :sellerId")
    LocalDateTime findLastListingDateBySellerUserId(@Param("sellerId") Long sellerId);
    
    // Find products with boost priority - boosted products appear first
    @Query("SELECT p FROM Product p LEFT JOIN ProductBoost b ON p.productId = b.product.productId " +
           "WHERE p.status = :status AND (b.boostEndTime > :now OR b.id IS NULL) " +
           "ORDER BY CASE WHEN b.id IS NOT NULL THEN 0 ELSE 1 END, p.createdAt DESC")
    Page<Product> findProductsWithBoostPriority(@Param("status") ProductStatus status, 
                                               @Param("now") LocalDateTime now, 
                                               Pageable pageable);
}
