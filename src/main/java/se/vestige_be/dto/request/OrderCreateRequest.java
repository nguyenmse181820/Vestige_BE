package se.vestige_be.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {
    @NotEmpty(message = "At least one product is required")
    @Valid
    private List<OrderItemRequest> items;

    @NotNull(message = "Shipping address ID is required")
    private Long shippingAddressId;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        private Long offerId;

        @Size(max = 200, message = "Item notes cannot exceed 200 characters")
        private String notes;
    }
}