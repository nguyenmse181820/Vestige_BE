package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import se.vestige_be.pojo.FeeTier;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.UserMembership;
import se.vestige_be.pojo.enums.MembershipStatus;
import se.vestige_be.repository.FeeTierRepository;
import se.vestige_be.repository.UserMembershipRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeTierService {

    private final FeeTierRepository feeTierRepository;
    private final UserMembershipRepository userMembershipRepository;

    /**
     * Calculate the platform fee for a given amount and seller
     */
    public BigDecimal calculatePlatformFee(BigDecimal amount, User seller) {
        BigDecimal feePercentage = calculateFeePercentage(amount, seller);
        return amount.multiply(feePercentage).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate the fee percentage for a given amount and seller
     */
    public BigDecimal calculateFeePercentage(BigDecimal amount, User seller) {
        // Find the appropriate fee tier
        FeeTier feeTier = feeTierRepository.findByMinValueLessThanEqualAndMaxValueGreaterThanEqual(amount, amount)
                .orElse(getDefaultFeeTier());

        BigDecimal baseFee = feeTier.getBaseFeePercentage();

        // Apply Legit Profile discount
        if (seller.getIsLegitProfile() && feeTier.getLegitProfileDiscount() != null) {
            baseFee = baseFee.subtract(feeTier.getLegitProfileDiscount());
        }

        // Apply membership discount if applicable
        if (hasActiveMembership(seller) && feeTier.getMembershipDiscount() != null) {
            baseFee = baseFee.subtract(feeTier.getMembershipDiscount());
        }

        // Ensure fee doesn't go below 0
        return baseFee.max(BigDecimal.ZERO);
    }

    /**
     * Get the default fee tier when no specific tier is found
     */
    private FeeTier getDefaultFeeTier() {
        return FeeTier.builder()
                .tierName("Default")
                .baseFeePercentage(new BigDecimal("5.0")) // 5% default fee
                .legitProfileDiscount(new BigDecimal("1.0")) // 1% discount for legit profiles
                .membershipDiscount(BigDecimal.ZERO)
                .build();
    }

    /**
     * Check if user has an active membership
     */
    private boolean hasActiveMembership(User seller) {
        Optional<UserMembership> activeMembership = userMembershipRepository
                .findByUserAndStatus(seller, MembershipStatus.ACTIVE);
        return activeMembership.isPresent();
    }

    /**
     * Calculate net amount seller receives after platform fee
     */
    public BigDecimal calculateSellerAmount(BigDecimal grossAmount, User seller) {
        BigDecimal platformFee = calculatePlatformFee(grossAmount, seller);
        return grossAmount.subtract(platformFee);
    }
}