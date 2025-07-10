package se.vestige_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import se.vestige_be.pojo.enums.TrustTier;

import java.math.BigDecimal;

@Data
public class MembershipPlanRequest {

    @NotBlank(message = "Plan name cannot be blank")
    private String name;

    private String description;

    @NotNull(message = "Price cannot be null")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @NotNull(message = "Boosts per month cannot be null")
    @Positive(message = "Boosts per month must be positive")
    private Integer boostsPerMonth;

    @NotBlank(message = "Stripe Price ID cannot be blank")
    private String stripePriceId;

    private TrustTier requiredTrustTier;

    @NotNull(message = "Fee Tier ID cannot be null")
    private Long feeTierId;

    private boolean isActive = true;
}