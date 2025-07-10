package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.pojo.FeeTier;
import se.vestige_be.pojo.MembershipPlan;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.UserMembership;
import se.vestige_be.pojo.enums.MembershipStatus;
import se.vestige_be.repository.MembershipPlanRepository;
import se.vestige_be.repository.UserMembershipRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeTierService {

    private final UserMembershipRepository userMembershipRepository;
    private final MembershipPlanRepository membershipPlanRepository;

    public BigDecimal calculatePlatformFee(BigDecimal amount, User seller) {
        BigDecimal feePercentage = getFeePercentageForSeller(seller);
        return amount.multiply(feePercentage).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getFeePercentageForSeller(User seller) {
        FeeTier applicableFeeTier = getFeeTierForSeller(seller);
        return applicableFeeTier.getFeePercentage();
    }

    private FeeTier getFeeTierForSeller(User seller) {
        Optional<UserMembership> activeMembershipOpt = userMembershipRepository
                .findByUserAndStatus(seller, MembershipStatus.ACTIVE);

        if (activeMembershipOpt.isPresent()) {
            return activeMembershipOpt.get().getPlan().getFeeTier();
        } else {
            MembershipPlan basicPlan = membershipPlanRepository.findAll()
                    .stream()
                    .filter(plan -> "Basic".equals(plan.getName()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessLogicException("System configuration error: Default membership plan is missing."));
            return basicPlan.getFeeTier();
        }
    }

    public BigDecimal calculateSellerAmount(BigDecimal grossAmount, User seller) {
        BigDecimal platformFee = calculatePlatformFee(grossAmount, seller);
        return grossAmount.subtract(platformFee);
    }
}
