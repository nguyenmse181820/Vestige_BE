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
    private Long sellerId;
    private String sellerUsername;
    private String sellerFirstName;
    private String sellerLastName;
    
    // Seller address information for pickup
    private Long sellerAddressId;
    private String sellerAddressLine1;
    private String sellerAddressLine2;
    private String sellerCity;
    private String sellerState;
    private String sellerPostalCode;
    private String sellerCountry;
    
    private BigDecimal price;
    private BigDecimal platformFee;
    private BigDecimal feePercentage;
    private OrderItemStatus status;
    private EscrowStatus escrowStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
