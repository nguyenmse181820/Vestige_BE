package se.vestige_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private Long reviewId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
    
    // Transaction info
    private Long transactionId;
    private BigDecimal transactionAmount;
    
    // Product info
    private ProductInfo product;
    
    // Reviewer info (buyer)
    private ReviewerInfo reviewer;
    
    // Reviewed user info (seller)
    private ReviewedUserInfo reviewedUser;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductInfo {
        private Long productId;
        private String title;
        private String primaryImageUrl;
        private String condition;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewerInfo {
        private Long userId;
        private String username;
        private String firstName;
        private String lastName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewedUserInfo {
        private Long userId;
        private String username;
        private String firstName;
        private String lastName;
        private BigDecimal sellerRating;
        private Integer sellerReviewsCount;
    }
}
