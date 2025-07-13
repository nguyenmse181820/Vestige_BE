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
public class UserStatisticsResponse {
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private LocalDateTime joinedDate;
    private String accountStatus;
    private Boolean isVerified;
    
    // Order statistics
    private Long totalOrders;
    private Long completedOrders;
    private Long pendingOrders;
    private Long cancelledOrders;
    private BigDecimal totalOrderValue;
    private BigDecimal averageOrderValue;
    
    // Product statistics
    private Long totalProductsListed;
    private Long activeProducts;
    private Long soldProducts;
    
    // Recent activity
    private LocalDateTime lastLoginDate;
    private LocalDateTime lastOrderDate;
    private LocalDateTime lastProductListingDate;
}
