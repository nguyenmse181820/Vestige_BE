package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.vestige_be.pojo.Review;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.enums.DisputeStatus;
import se.vestige_be.pojo.enums.OrderItemStatus;
import se.vestige_be.pojo.enums.TrustTier;
import se.vestige_be.repository.OrderItemRepository;
import se.vestige_be.repository.ReviewRepository;
import se.vestige_be.repository.TransactionRepository;
import se.vestige_be.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrustScoreService {

    private final OrderItemRepository orderItemRepository;
    private final TransactionRepository transactionRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public void updateUserTrustScore(User user) {
        // Calculate scores for 6, 12, and 24 month windows
        int score6Months = calculateOverallScoreForWindow(user, 6);
        int score12Months = calculateOverallScoreForWindow(user, 12);
        int score24Months = calculateOverallScoreForWindow(user, 24);

        // Find the best score among the windows
        int bestScore = Collections.max(Arrays.asList(score6Months, score12Months, score24Months));

        // Determine the tier based on the best score and lifetime sales
        long totalCompletedSales = orderItemRepository.countBySellerAndStatusAndCreatedAtAfter(user, OrderItemStatus.DELIVERED, LocalDateTime.now().minusYears(10));
        TrustTier tier = determineTrustTier(bestScore, totalCompletedSales, user.getIsVerified());

        // Update user and save
        user.setTrustScore(bestScore);
        user.setTrustTier(tier);
        userRepository.save(user);
    }

    private int calculateOverallScoreForWindow(User user, int months) {
        double performanceScore = calculatePerformanceScore(user, months);
        double reviewScore = calculateReviewScore(user, months);
//        double profileScore = calculateProfileScore(user);

//        double overallScore = (performanceScore * 0.60) + (reviewScore * 0.25) + (profileScore * 0.15);
        double overallScore = (performanceScore * 0.70) + (reviewScore * 0.30);
        return (int) Math.round(overallScore);
    }

    private double calculatePerformanceScore(User user, int months) {
        LocalDateTime since = LocalDateTime.now().minusMonths(months);
        long completedOrders = orderItemRepository.countBySellerAndStatusAndCreatedAtAfter(user, OrderItemStatus.DELIVERED, since);
        long cancelledOrders = orderItemRepository.countBySellerAndStatusAndCreatedAtAfter(user, OrderItemStatus.CANCELLED, since);
        long lostDisputes = transactionRepository.countBySellerAndDisputeStatusAndCreatedAtAfter(user, DisputeStatus.FAVOR_BUYER, since);

        double completionRate = 100.0;
        if (completedOrders + cancelledOrders > 0) {
            completionRate = ((double) completedOrders / (completedOrders + cancelledOrders)) * 100;
        }

        double disputeRateScore = 100.0;
        if (completedOrders > 0) {
            disputeRateScore = (1.0 - ((double) lostDisputes / completedOrders)) * 100;
        }

        return (completionRate * 0.8) + (disputeRateScore * 0.2);
    }

    private double calculateReviewScore(User user, int months) {
        LocalDateTime since = LocalDateTime.now().minusMonths(months);
        List<Review> reviews = reviewRepository.findByReviewedUserAndCreatedAtAfter(user, since);

        if (reviews.isEmpty()) {
            return 70;
        }

        double totalWeightedRating = 0;
        double totalDecayFactor = 0;

        for (Review review : reviews) {
            long yearsAgo = ChronoUnit.YEARS.between(review.getCreatedAt(), LocalDateTime.now());
            double decayFactor = 1.0 / (1.0 + 0.5 * yearsAgo);
            totalWeightedRating += review.getRating() * decayFactor;
            totalDecayFactor += decayFactor;
        }

        double timeDecayedAverageRating = totalWeightedRating / totalDecayFactor;
        return (timeDecayedAverageRating / 5.0) * 100;
    }

    private double calculateProfileScore(User user) {
        double score = 0;
        if (user.getIsVerified()) score += 50;
        if (user.getStripeAccountId() != null && !user.getStripeAccountId().isEmpty()) score += 25;
        if (user.getProfilePictureUrl() != null && user.getBio() != null) score += 10;

        long monthsSinceCreation = ChronoUnit.MONTHS.between(user.getJoinedDate(), LocalDateTime.now());
        score += Math.min(monthsSinceCreation * 2, 15); // Capped at 15 points

        return score;
    }

    private TrustTier determineTrustTier(int score, long totalCompletedSales, boolean isVerified) {
        if (score >= 95 && totalCompletedSales >= 100) return TrustTier.ELITE_SELLER;
        if (score >= 85 && totalCompletedSales >= 25) return TrustTier.PRO_SELLER;
        if (score >= 70 && totalCompletedSales >= 10) return TrustTier.RISING_SELLER;
        return TrustTier.NEW_SELLER;
    }

    /**
     * Manually update trust scores for all users. This can be called by an admin endpoint.
     */
    public void updateAllUserTrustScores() {
        userRepository.findAll().forEach(this::updateUserTrustScore);
    }

    /**
     * Get the calculated trust score for a user without persisting it.
     * Useful for preview/testing purposes.
     */
    public int calculateTrustScorePreview(User user) {
        int score6Months = calculateOverallScoreForWindow(user, 6);
        int score12Months = calculateOverallScoreForWindow(user, 12);
        int score24Months = calculateOverallScoreForWindow(user, 24);
        return Collections.max(Arrays.asList(score6Months, score12Months, score24Months));
    }
}
