package se.vestige_be.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemRatingStatsResponse {
    private Double averageRating;
    private Long totalRatings;
    private Long oneStarCount;
    private Long twoStarCount;
    private Long threeStarCount;
    private Long fourStarCount;
    private Long fiveStarCount;
}
