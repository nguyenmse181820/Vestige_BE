package se.vestige_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import se.vestige_be.pojo.SystemRating;
import se.vestige_be.pojo.User;
import java.util.Optional;

@Repository
public interface SystemRatingRepository extends JpaRepository<SystemRating, Long> {
    Optional<SystemRating> findByUserUserId(Long userId);
    
    @Query("SELECT sr FROM SystemRating sr LEFT JOIN FETCH sr.user WHERE sr.user.userId = :userId")
    Optional<SystemRating> findByUserUserIdWithUser(Long userId);
    
    @Query("SELECT AVG(sr.rating) FROM SystemRating sr")
    Double findAverageRating();
    
    @Query("SELECT COUNT(sr) FROM SystemRating sr")
    Long findTotalRatings();
    
    @Query("SELECT COUNT(sr) FROM SystemRating sr WHERE sr.rating = :rating")
    Long countByRating(int rating);
    
    // Get all ratings with user information for pagination
    @Query(value = "SELECT sr FROM SystemRating sr LEFT JOIN FETCH sr.user ORDER BY sr.createdAt DESC",
           countQuery = "SELECT COUNT(sr) FROM SystemRating sr")
    Page<SystemRating> findAllWithUser(Pageable pageable);
}
