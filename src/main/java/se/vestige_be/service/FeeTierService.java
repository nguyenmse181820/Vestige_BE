package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.vestige_be.pojo.FeeTier;
import se.vestige_be.pojo.User;
import se.vestige_be.repository.FeeTierRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeeTierService {

    private final FeeTierRepository feeTierRepository;

    public BigDecimal calculatePlatformFee(BigDecimal amount, User seller) {
        FeeTier tier = getApplicableFeeTier(amount);
        BigDecimal feePercentage = calculateFeePercentage(amount, seller);

        return amount.multiply(feePercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateFeePercentage(BigDecimal amount, User seller) {
        FeeTier tier = getApplicableFeeTier(amount);
        BigDecimal baseFeePercentage = tier.getBaseFeePercentage();

        // Apply discounts
        if (seller.getIsLegitProfile()) {
            baseFeePercentage = baseFeePercentage.subtract(tier.getLegitProfileDiscount());
        }

        // Apply membership discount if user has active membership
        // This would need to check for active membership
        // baseFeePercentage = baseFeePercentage.subtract(tier.getMembershipDiscount());

        return baseFeePercentage.max(BigDecimal.valueOf(1.0)); // Minimum 1%
    }

    private FeeTier getApplicableFeeTier(BigDecimal amount) {
        List<FeeTier> tiers = feeTierRepository.findAll();

        return tiers.stream()
                .filter(tier -> {
                    boolean minMatch = tier.getMinValue().compareTo(amount) <= 0;
                    boolean maxMatch = tier.getMaxValue() == null || tier.getMaxValue().compareTo(amount) >= 0;
                    return minMatch && maxMatch;
                })
                .findFirst()
                .orElse(tiers.getFirst()); // Default to first tier if none match
    }
}