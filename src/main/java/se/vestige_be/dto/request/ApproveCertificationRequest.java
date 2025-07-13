package se.vestige_be.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@NoArgsConstructor
public class ApproveCertificationRequest {
    
    @NotNull(message = "Expiration months cannot be null")
    @Positive(message = "Expiration months must be positive")
    private Integer expirationMonths = 12; // Default to 12 months
}
