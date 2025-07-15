package se.vestige_be.dto.response;

import lombok.*;
import se.vestige_be.pojo.enums.EscrowStatus;
import se.vestige_be.pojo.enums.OrderItemStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickupItemResponse {
    private Long orderItemId;
    private Long orderId;
    private String orderCode;
    private Long productId;
    private String productName;
    private String productSlug;
    private BigDecimal price;
    private BigDecimal platformFee;
    private BigDecimal feePercentage;
    private OrderItemStatus status;
    private EscrowStatus escrowStatus;
    private Instant createdAt;
    private Instant updatedAt;
    private SellerInfo sellerInfo;
    private BuyerInfo buyerInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerInfo {
        private Long sellerId;
        private String sellerUsername;
        private String sellerFirstName;
        private String sellerLastName;
        private Long sellerAddressId;
        private String sellerAddressLine1;
        private String sellerAddressLine2;
        private String sellerCity;
        private String sellerState;
        private String sellerPostalCode;
        private String sellerCountry;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BuyerInfo {
        private Long buyerId;
        private String buyerUsername;
        private String buyerFirstName;
        private String buyerLastName;
        private Long buyerAddressId;
        private String buyerAddressLine1;
        private String buyerAddressLine2;
        private String buyerCity;
        private String buyerState;
        private String buyerPostalCode;
        private String buyerCountry;
    }
}
