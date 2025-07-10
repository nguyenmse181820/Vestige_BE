package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.Review;
import se.vestige_be.pojo.User;
import java.time.LocalDateTime;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    // Find all reviews for a user within a specific time window
    List<Review> findByReviewedUserAndCreatedAtAfter(User reviewedUser, LocalDateTime date);
}
