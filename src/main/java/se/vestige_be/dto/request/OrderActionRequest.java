package se.vestige_be.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderActionRequest {
    
    @NotNull(message = "Order ID is required")
    @JsonProperty("orderId")
    private Long orderId;
    
    @JsonProperty("reason")
    private String reason;
    
    @JsonProperty("notes")
    private String notes;
}
