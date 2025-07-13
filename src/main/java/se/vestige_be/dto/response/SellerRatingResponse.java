package se.vestige_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerRatingResponse {
    private Long sellerId;
    private String sellerUsername;
    private String sellerName;
    private BigDecimal averageRating;
    private Integer totalReviews;
    private RatingBreakdown ratingBreakdown;
    private List<ReviewResponse> recentReviews;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatingBreakdown {
        private Integer fiveStars;
        private Integer fourStars;
        private Integer threeStars;
        private Integer twoStars;
        private Integer oneStar;
    }
}
