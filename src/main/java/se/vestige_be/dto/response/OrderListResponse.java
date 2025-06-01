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
public class OrderListResponse {
    private Long orderId;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal totalShippingFee;
    private Integer totalItems;
    private Integer uniqueSellers;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime deliveredAt;

    private List<OrderItemSummary> itemSummaries;

    private String overallEscrowStatus;
    private BigDecimal totalPlatformFee;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemSummary {
        private Long productId;
        private String productTitle;
        private String productImage;
        private BigDecimal price;
        private String sellerUsername;
        private Boolean sellerIsLegitProfile;
        private String escrowStatus;
        private String itemStatus;
    }
}