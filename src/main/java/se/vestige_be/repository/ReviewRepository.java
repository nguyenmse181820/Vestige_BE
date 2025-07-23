package se.vestige_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.Review;
import se.vestige_be.pojo.Transaction;
import se.vestige_be.pojo.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    
    // Find all reviews for a user within a specific time window
    List<Review> findByReviewedUserAndCreatedAtAfter(User reviewedUser, LocalDateTime date);
    
    // Find all reviews for a specific seller
    Page<Review> findByReviewedUserOrderByCreatedAtDesc(User reviewedUser, Pageable pageable);
    
    // Find all reviews by a specific reviewer
    Page<Review> findByReviewerOrderByCreatedAtDesc(User reviewer, Pageable pageable);
    
    // Check if a review already exists for a transaction
    Optional<Review> findByTransaction(Transaction transaction);
    
    // Check if a reviewer has already reviewed a specific transaction
    boolean existsByTransactionAndReviewer(Transaction transaction, User reviewer);
    
    // Get rating statistics for a seller
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.reviewedUser = :seller")
    Double getAverageRatingForSeller(@Param("seller") User seller);
    
    @Query("SELECT COUNT(r) FROM Review r WHERE r.reviewedUser = :seller")
    Long getTotalReviewsForSeller(@Param("seller") User seller);
    
    // Get rating breakdown for a seller
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.reviewedUser = :seller GROUP BY r.rating")
    List<Object[]> getRatingBreakdownForSeller(@Param("seller") User seller);
    
    // Get recent reviews for a seller with transaction details
    @Query("SELECT r FROM Review r " +
           "JOIN FETCH r.transaction t " +
           "JOIN FETCH t.orderItem oi " +
           "JOIN FETCH oi.product p " +
           "WHERE r.reviewedUser = :seller " +
           "ORDER BY r.createdAt DESC")
    List<Review> findRecentReviewsForSellerWithDetails(@Param("seller") User seller, Pageable pageable);
    
    // Enhanced methods with complete relationship loading for ReviewService
    @Query(value = "SELECT r FROM Review r " +
           "LEFT JOIN FETCH r.reviewer " +
           "LEFT JOIN FETCH r.reviewedUser " +
           "LEFT JOIN FETCH r.transaction t " +
           "LEFT JOIN FETCH t.orderItem oi " +
           "LEFT JOIN FETCH oi.product p " +
           "WHERE r.reviewedUser = :reviewedUser " +
           "ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM Review r WHERE r.reviewedUser = :reviewedUser")
    Page<Review> findByReviewedUserWithAllRelationships(@Param("reviewedUser") User reviewedUser, Pageable pageable);
    
    @Query(value = "SELECT r FROM Review r " +
           "LEFT JOIN FETCH r.reviewer " +
           "LEFT JOIN FETCH r.reviewedUser " +
           "LEFT JOIN FETCH r.transaction t " +
           "LEFT JOIN FETCH t.orderItem oi " +
           "LEFT JOIN FETCH oi.product p " +
           "WHERE r.reviewer = :reviewer " +
           "ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM Review r WHERE r.reviewer = :reviewer")
    Page<Review> findByReviewerWithAllRelationships(@Param("reviewer") User reviewer, Pageable pageable);
}
