package se.vestige_be.dto.response;

import lombok.*;
import se.vestige_be.pojo.enums.OrderItemStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long orderItemId;
    private Long orderId;
    private String orderCode;
    private Long productId;
    private String productName;
    private String productSlug;
    private Long sellerId;
    private String sellerUsername;
    private BigDecimal price;
    private BigDecimal platformFee;
    private BigDecimal feePercentage;
    private OrderItemStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
