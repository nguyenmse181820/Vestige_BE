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
public class ProductListResponse {
    private Long productId;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String condition;
    private String size;
    private String color;
    private BigDecimal shippingFee;
    private String status;
    private Integer viewsCount;
    private Integer likesCount;
    private LocalDateTime createdAt;

    private Long sellerId;
    private String sellerUsername;
    private Boolean sellerIsLegitProfile;
    private BigDecimal sellerRating;

    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;

    private String primaryImageUrl;

    private BigDecimal discountPercentage;
    private Boolean hasDiscount;
}
