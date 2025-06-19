package se.vestige_be.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDetailResponse {
    private Long orderId;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal totalShippingFee;
    private BigDecimal totalPlatformFee;
    private String notes;
    private Integer totalItems;
    private Integer uniqueSellers;
    private LocalDateTime createdAt;    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private String stripePaymentIntentId;

    private List<OrderItemDetail> orderItems;
    private ShippingAddressInfo shippingAddress;
    private OrderEscrowSummary escrowSummary;

    private Map<String, List<OrderItemDetail>> itemsBySeller;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDetail {
        private Long orderItemId;
        private BigDecimal price;
        private BigDecimal platformFee;
        private BigDecimal feePercentage;
        private String status;
        private String escrowStatus;
        private String notes;

        private ProductInfo product;
        private SellerInfo seller;
        private TransactionInfo transaction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductInfo {
        private Long productId;
        private String title;
        private String description;
        private String condition;
        private String size;
        private String color;
        private String primaryImageUrl;
        private BigDecimal shippingFee;
        private Long categoryId;
        private String categoryName;
        private Long brandId;
        private String brandName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerInfo {
        private Long userId;
        private String username;
        private String firstName;
        private String lastName;
        private Boolean isLegitProfile;
        private BigDecimal sellerRating;
        private Integer sellerReviewsCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionInfo {
        private Long transactionId;
        private String trackingNumber;
        private String trackingUrl;
        private LocalDateTime shippedAt;
        private LocalDateTime deliveredAt;
        private Boolean buyerProtectionEligible;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddressInfo {
        private Long addressId;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderEscrowSummary {
        private BigDecimal totalEscrowAmount;
        private BigDecimal totalPlatformFee;
        private BigDecimal totalSellerAmount;
        private Integer itemsInEscrow;
        private Integer itemsReleased;
        private Integer itemsRefunded;
        private List<SellerEscrowInfo> sellerEscrows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerEscrowInfo {
        private Long sellerId;
        private String sellerUsername;
        private BigDecimal escrowAmount;
        private BigDecimal platformFee;
        private BigDecimal sellerAmount;
        private String escrowStatus;
        private Integer itemCount;
    }
}