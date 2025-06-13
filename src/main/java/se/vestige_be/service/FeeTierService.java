package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import se.vestige_be.pojo.FeeTier;
import se.vestige_be.pojo.User;
import se.vestige_be.repository.FeeTierRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeTierService {

    private final FeeTierRepository feeTierRepository;

    // Default fee structure if no fee tiers configured
    private static final BigDecimal DEFAULT_BASE_FEE = new BigDecimal("5.0"); // 5%
    private static final BigDecimal LEGIT_PROFILE_DISCOUNT = new BigDecimal("1.0"); // 1% discount
    private static final BigDecimal MEMBERSHIP_DISCOUNT = new BigDecimal("0.5"); // 0.5% discount

    /**
     * Calculate platform fee amount
     */
    public BigDecimal calculatePlatformFee(BigDecimal amount, User seller) {
        BigDecimal feePercentage = calculateFeePercentage(amount, seller);
        return amount.multiply(feePercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate fee percentage based on amount and seller profile
     */
    public BigDecimal calculateFeePercentage(BigDecimal amount, User seller) {
        // Find applicable fee tier
        FeeTier feeTier = findApplicableFeeTier(amount);

        BigDecimal baseFee = feeTier != null ?
                feeTier.getBaseFeePercentage() : DEFAULT_BASE_FEE;

        // Apply discounts
        BigDecimal finalFee = baseFee;

        // Legit Profile discount
        if (seller.getIsLegitProfile() != null && seller.getIsLegitProfile()) {
            BigDecimal discount = feeTier != null && feeTier.getLegitProfileDiscount() != null ?
                    feeTier.getLegitProfileDiscount() : LEGIT_PROFILE_DISCOUNT;
            finalFee = finalFee.subtract(discount);
        }

        // Membership discount (if user has active membership)
        if (hasActiveMembership(seller)) {
            BigDecimal discount = feeTier != null && feeTier.getMembershipDiscount() != null ?
                    feeTier.getMembershipDiscount() : MEMBERSHIP_DISCOUNT;
            finalFee = finalFee.subtract(discount);
        }

        // Ensure minimum fee of 2%
        BigDecimal minimumFee = new BigDecimal("2.0");
        if (finalFee.compareTo(minimumFee) < 0) {
            finalFee = minimumFee;
        }

        log.debug("Fee calculation for seller {}: base={}%, final={}%",
                seller.getUsername(), baseFee, finalFee);

        return finalFee;
    }

    /**
     * Find applicable fee tier based on transaction amount
     */
    private FeeTier findApplicableFeeTier(BigDecimal amount) {
        return feeTierRepository
                .findByMinValueLessThanEqualAndMaxValueGreaterThanEqual(amount, amount)
                .orElse(null);
    }

    /**
     * Check if seller has active membership
     */
    private boolean hasActiveMembership(User seller) {
        // Check if seller has any active memberships
        return seller.getMemberships() != null &&
                seller.getMemberships().stream()
                        .anyMatch(membership ->
                                se.vestige_be.pojo.enums.MembershipStatus.ACTIVE.equals(membership.getStatus()));
    }

    /**
     * Get fee breakdown for display
     */
    public FeeBreakdown getFeeBreakdown(BigDecimal amount, User seller) {
        FeeTier feeTier = findApplicableFeeTier(amount);
        BigDecimal baseFeePercentage = feeTier != null ?
                feeTier.getBaseFeePercentage() : DEFAULT_BASE_FEE;

        BigDecimal baseFeeAmount = amount.multiply(baseFeePercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal legitDiscount = BigDecimal.ZERO;
        if (seller.getIsLegitProfile() != null && seller.getIsLegitProfile()) {
            BigDecimal discountPercentage = feeTier != null && feeTier.getLegitProfileDiscount() != null ?
                    feeTier.getLegitProfileDiscount() : LEGIT_PROFILE_DISCOUNT;
            legitDiscount = amount.multiply(discountPercentage)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        BigDecimal membershipDiscount = BigDecimal.ZERO;
        if (hasActiveMembership(seller)) {
            BigDecimal discountPercentage = feeTier != null && feeTier.getMembershipDiscount() != null ?
                    feeTier.getMembershipDiscount() : MEMBERSHIP_DISCOUNT;
            membershipDiscount = amount.multiply(discountPercentage)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        BigDecimal totalFee = calculatePlatformFee(amount, seller);
        BigDecimal sellerReceives = amount.subtract(totalFee);

        return FeeBreakdown.builder()
                .transactionAmount(amount)
                .baseFeePercentage(baseFeePercentage)
                .baseFeeAmount(baseFeeAmount)
                .legitProfileDiscount(legitDiscount)
                .membershipDiscount(membershipDiscount)
                .totalPlatformFee(totalFee)
                .sellerReceives(sellerReceives)
                .finalFeePercentage(calculateFeePercentage(amount, seller))
                .build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FeeBreakdown {
        private BigDecimal transactionAmount;
        private BigDecimal baseFeePercentage;
        private BigDecimal baseFeeAmount;
        private BigDecimal legitProfileDiscount;
        private BigDecimal membershipDiscount;
        private BigDecimal totalPlatformFee;
        private BigDecimal sellerReceives;
        private BigDecimal finalFeePercentage;
    }
}