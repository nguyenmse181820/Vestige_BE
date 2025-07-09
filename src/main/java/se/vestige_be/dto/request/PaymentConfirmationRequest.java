package se.vestige_be.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmationRequest {
    
    @NotNull(message = "Order ID is required")
    @JsonProperty("orderId")
    private Long orderId;
    
    @NotBlank(message = "Stripe payment intent ID is required")
    @JsonProperty("stripePaymentIntentId")
    private String stripePaymentIntentId;
    
    // Optional: Add client secret for additional validation
    @JsonProperty("clientSecret")
    private String clientSecret;
}