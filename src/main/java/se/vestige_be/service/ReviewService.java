package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.request.CreateReviewRequest;
import se.vestige_be.dto.response.PagedResponse;
import se.vestige_be.dto.response.ReviewResponse;
import se.vestige_be.dto.response.SellerRatingResponse;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.exception.ResourceNotFoundException;
import se.vestige_be.exception.UnauthorizedException;
import se.vestige_be.pojo.*;
import se.vestige_be.pojo.enums.OrderItemStatus;
import se.vestige_be.pojo.enums.TransactionStatus;
import se.vestige_be.repository.ReviewRepository;
import se.vestige_be.repository.TransactionRepository;
import se.vestige_be.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /**
     * Create a review for a seller after completing a transaction
     */
    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request, Long reviewerId) {
        // Get the reviewer (buyer)
        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + reviewerId));

        // Get the transaction
        Transaction transaction = transactionRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + request.getTransactionId()));

        // Validate that the reviewer is the buyer of this transaction
        if (!transaction.getBuyer().getUserId().equals(reviewerId)) {
            throw new UnauthorizedException("You can only review transactions where you are the buyer");
        }

        // Validate that the transaction is completed (delivered)
        if (transaction.getStatus() != TransactionStatus.DELIVERED || 
            transaction.getOrderItem().getStatus() != OrderItemStatus.DELIVERED) {
            throw new BusinessLogicException("You can only review completed transactions");
        }

        // Check if review already exists
        if (reviewRepository.existsByTransactionAndReviewer(transaction, reviewer)) {
            throw new BusinessLogicException("You have already reviewed this transaction");
        }

        // Validate that some time has passed since delivery (optional - prevents immediate reviews)
        if (transaction.getDeliveredAt() != null && 
            transaction.getDeliveredAt().isAfter(LocalDateTime.now().minusHours(1))) {
            log.warn("Review submitted very quickly after delivery for transaction {}", transaction.getTransactionId());
        }

        // Create the review
        Review review = Review.builder()
                .transaction(transaction)
                .reviewer(reviewer)
                .reviewedUser(transaction.getSeller())
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        review = reviewRepository.save(review);

        // Update seller's rating statistics
        updateSellerRatingStatistics(transaction.getSeller());

        log.info("Review created: {} stars for seller {} by buyer {} for transaction {}", 
                request.getRating(), transaction.getSeller().getUsername(), 
                reviewer.getUsername(), transaction.getTransactionId());

        return convertToReviewResponse(review);
    }

    /**
     * Get all reviews for a specific seller
     */
    public PagedResponse<ReviewResponse> getSellerReviews(Long sellerId, Pageable pageable) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found: " + sellerId));

        Page<Review> reviews = reviewRepository.findByReviewedUserWithAllRelationships(seller, pageable);
        
        Page<ReviewResponse> reviewResponses = reviews.map(this::convertToReviewResponse);
        return PagedResponse.of(reviewResponses);
    }

    /**
     * Get all reviews made by a specific user (as buyer)
     */
    public PagedResponse<ReviewResponse> getUserReviews(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Page<Review> reviews = reviewRepository.findByReviewerWithAllRelationships(user, pageable);
        
        Page<ReviewResponse> reviewResponses = reviews.map(this::convertToReviewResponse);
        return PagedResponse.of(reviewResponses);
    }

    /**
     * Get comprehensive seller rating information
     */
    public SellerRatingResponse getSellerRating(Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found: " + sellerId));

        // Get rating statistics
        Double averageRating = reviewRepository.getAverageRatingForSeller(seller);
        Long totalReviews = reviewRepository.getTotalReviewsForSeller(seller);

        // Get rating breakdown
        List<Object[]> ratingBreakdownData = reviewRepository.getRatingBreakdownForSeller(seller);
        SellerRatingResponse.RatingBreakdown breakdown = buildRatingBreakdown(ratingBreakdownData);

        // Get recent reviews
        List<Review> recentReviews = reviewRepository.findRecentReviewsForSellerWithDetails(
                seller, PageRequest.of(0, 5));
        List<ReviewResponse> recentReviewResponses = recentReviews.stream()
                .map(this::convertToReviewResponse)
                .collect(Collectors.toList());

        return SellerRatingResponse.builder()
                .sellerId(seller.getUserId())
                .sellerUsername(seller.getUsername())
                .sellerName(seller.getFirstName() + " " + seller.getLastName())
                .averageRating(averageRating != null ? 
                        BigDecimal.valueOf(averageRating).setScale(2, RoundingMode.HALF_UP) : 
                        BigDecimal.ZERO)
                .totalReviews(totalReviews.intValue())
                .ratingBreakdown(breakdown)
                .recentReviews(recentReviewResponses)
                .build();
    }

    /**
     * Check if a user can review a specific transaction
     */
    public boolean canReviewTransaction(Long transactionId, Long userId) {
        Optional<Transaction> transactionOpt = transactionRepository.findById(transactionId);
        if (transactionOpt.isEmpty()) {
            return false;
        }

        Transaction transaction = transactionOpt.get();

        // Must be the buyer
        if (!transaction.getBuyer().getUserId().equals(userId)) {
            return false;
        }

        // Transaction must be delivered
        if (transaction.getStatus() != TransactionStatus.DELIVERED ||
            transaction.getOrderItem().getStatus() != OrderItemStatus.DELIVERED) {
            return false;
        }

        // Must not have already reviewed
        User buyer = transaction.getBuyer();
        if (reviewRepository.existsByTransactionAndReviewer(transaction, buyer)) {
            return false;
        }

        return true;
    }

    /**
     * Get review for a specific transaction (if exists)
     */
    public Optional<ReviewResponse> getReviewForTransaction(Long transactionId) {
        Optional<Transaction> transactionOpt = transactionRepository.findById(transactionId);
        if (transactionOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<Review> reviewOpt = reviewRepository.findByTransaction(transactionOpt.get());
        return reviewOpt.map(this::convertToReviewResponse);
    }

    /**
     * Update seller's cached rating statistics
     */
    @Transactional
    public void updateSellerRatingStatistics(User seller) {
        Double averageRating = reviewRepository.getAverageRatingForSeller(seller);
        Long totalReviews = reviewRepository.getTotalReviewsForSeller(seller);

        seller.setSellerRating(averageRating != null ? 
                BigDecimal.valueOf(averageRating).setScale(2, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO);
        seller.setSellerReviewsCount(totalReviews.intValue());

        userRepository.save(seller);
        
        log.debug("Updated seller {} rating to {} based on {} reviews", 
                seller.getUsername(), seller.getSellerRating(), seller.getSellerReviewsCount());
    }

    /**
     * Convert Review entity to ReviewResponse DTO
     */
    private ReviewResponse convertToReviewResponse(Review review) {
        if (review == null) {
            return null;
        }

        // Safely access transaction and nested relationships
        Transaction transaction = null;
        OrderItem orderItem = null;
        Product product = null;
        
        try {
            transaction = review.getTransaction();
            if (transaction != null) {
                orderItem = transaction.getOrderItem();
                if (orderItem != null) {
                    product = orderItem.getProduct();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load transaction relationships for review {}: {}", review.getReviewId(), e.getMessage());
        }

        // Build product info safely
        ReviewResponse.ProductInfo productInfo = null;
        if (product != null) {
            try {
                productInfo = ReviewResponse.ProductInfo.builder()
                        .productId(product.getProductId())
                        .title(product.getTitle())
                        .primaryImageUrl(getProductImageUrl(product))
                        .condition(product.getCondition() != null ? product.getCondition().toString() : "UNKNOWN")
                        .build();
            } catch (Exception e) {
                log.warn("Failed to build product info for review {}: {}", review.getReviewId(), e.getMessage());
            }
        }

        // Build reviewer info safely
        ReviewResponse.ReviewerInfo reviewerInfo = null;
        try {
            if (review.getReviewer() != null) {
                User reviewer = review.getReviewer();
                reviewerInfo = ReviewResponse.ReviewerInfo.builder()
                        .userId(reviewer.getUserId())
                        .username(reviewer.getUsername())
                        .firstName(reviewer.getFirstName())
                        .lastName(reviewer.getLastName())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to load reviewer info for review {}: {}", review.getReviewId(), e.getMessage());
        }

        // Build reviewed user info safely
        ReviewResponse.ReviewedUserInfo reviewedUserInfo = null;
        try {
            if (review.getReviewedUser() != null) {
                User reviewedUser = review.getReviewedUser();
                reviewedUserInfo = ReviewResponse.ReviewedUserInfo.builder()
                        .userId(reviewedUser.getUserId())
                        .username(reviewedUser.getUsername())
                        .firstName(reviewedUser.getFirstName())
                        .lastName(reviewedUser.getLastName())
                        .sellerRating(reviewedUser.getSellerRating())
                        .sellerReviewsCount(reviewedUser.getSellerReviewsCount())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to load reviewed user info for review {}: {}", review.getReviewId(), e.getMessage());
        }

        return ReviewResponse.builder()
                .reviewId(review.getReviewId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .transactionId(transaction != null ? transaction.getTransactionId() : null)
                .transactionAmount(transaction != null ? transaction.getAmount() : null)
                .product(productInfo)
                .reviewer(reviewerInfo)
                .reviewedUser(reviewedUserInfo)
                .build();
    }

    /**
     * Build rating breakdown from raw data
     */
    private SellerRatingResponse.RatingBreakdown buildRatingBreakdown(List<Object[]> ratingData) {
        Map<Integer, Integer> ratingCounts = new HashMap<>();
        
        // Initialize all ratings to 0
        for (int i = 1; i <= 5; i++) {
            ratingCounts.put(i, 0);
        }

        // Populate with actual data
        for (Object[] row : ratingData) {
            Integer rating = (Integer) row[0];
            Long count = (Long) row[1];
            ratingCounts.put(rating, count.intValue());
        }

        return SellerRatingResponse.RatingBreakdown.builder()
                .fiveStars(ratingCounts.get(5))
                .fourStars(ratingCounts.get(4))
                .threeStars(ratingCounts.get(3))
                .twoStars(ratingCounts.get(2))
                .oneStar(ratingCounts.get(1))
                .build();
    }

    /**
     * Get product image URL (simplified version) with lazy loading protection
     */
    private String getProductImageUrl(Product product) {
        if (product == null) {
            return null;
        }
        
        try {
            List<ProductImage> images = product.getImages();
            if (images != null && !images.isEmpty()) {
                return images.get(0).getImageUrl();
            }
        } catch (Exception e) {
            log.warn("Failed to load product images for product {}: {}", product.getProductId(), e.getMessage());
        }
        
        return null;
    }
}
