package se.vestige_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSellersResponse {
    private Integer sellerCount;
    private Map<Long, List<OrderDetailResponse.OrderItemDetail>> itemsBySeller;
    private List<SellerSummary> sellerSummaries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerSummary {
        private Long sellerId;
        private String sellerUsername;
        private String sellerName;
        private Boolean isLegitProfile;
        private Integer itemCount;
        private String overallStatus;
    }
}