package se.vestige_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailResponse {
    private Long productId;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String condition;
    private String size;
    private String color;
    private BigDecimal authenticityConfidenceScore;
    private String status;
    private Integer viewsCount;
    private Integer likesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private SellerInfo seller;
    private CategoryInfo category;
    private BrandInfo brand;
    private List<ProductImageInfo> images;
    private BigDecimal discountPercentage;
    private Boolean hasDiscount;

    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerInfo {
        private Long userId;
        private String username;
        private String firstName;
        private String lastName;
        private String profilePictureUrl;
        private Boolean isLegitProfile;
        private BigDecimal sellerRating;
        private Integer sellerReviewsCount;
        private Integer successfulTransactions;
        private LocalDateTime joinedDate;
    }

    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryInfo {
        private Long categoryId;
        private String name;
        private String description;
    }

    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandInfo {
        private Long brandId;
        private String name;
        private String logoUrl;
    }

    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductImageInfo {
        private Long imageId;
        private String imageUrl;
        private Boolean isPrimary;
        private Integer displayOrder;
        private Boolean active;
    }
}