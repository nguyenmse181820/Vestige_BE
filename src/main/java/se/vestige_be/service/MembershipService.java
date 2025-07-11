package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.exception.ResourceNotFoundException;
import se.vestige_be.pojo.*;
import se.vestige_be.pojo.enums.MembershipStatus;
import se.vestige_be.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipService {

    private final UserMembershipRepository userMembershipRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final ProductBoostRepository productBoostRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StripeService stripeService;

    @Transactional(readOnly = true)
    public List<MembershipPlan> getAllPlans() {
        return membershipPlanRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<UserMembership> getActiveMembership(UserDetails currentUserDetails) {
        User user = userRepository.findByUsername(currentUserDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(currentUserDetails.getUsername()));
        return userMembershipRepository.findByUserAndStatus(user, MembershipStatus.ACTIVE);
    }

    @Transactional
    public String subscribe(UserDetails currentUserDetails, Long planId) {
        User user = userRepository.findByUsername(currentUserDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(currentUserDetails.getUsername()));

        if (userMembershipRepository.findByUserAndStatus(user, MembershipStatus.ACTIVE).isPresent()) {
            throw new BusinessLogicException("User already has an active subscription.");
        }

        MembershipPlan plan = membershipPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessLogicException("Membership plan not found."));

        if (plan.getRequiredTrustTier() != null) {
            if (user.getTrustTier() == null || user.getTrustTier().getLevel() < plan.getRequiredTrustTier().getLevel()) {
                throw new BusinessLogicException("You do not meet the required Trust Tier for this plan. Required: " + plan.getRequiredTrustTier());
            }
        }

        UserMembership userMembership = UserMembership.builder()
                .user(user)
                .plan(plan)
                .status(MembershipStatus.PENDING)
                .boostsRemaining(0)
                .build();
        userMembershipRepository.save(userMembership);

        // Pass user and plan IDs to Stripe for the webhook
        return stripeService.createSubscriptionCheckoutSession(user, plan);
    }

    @Transactional
    public void cancelSubscription(UserDetails currentUserDetails) {
        User user = userRepository.findByUsername(currentUserDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(currentUserDetails.getUsername()));

        UserMembership activeMembership = userMembershipRepository.findByUserAndStatus(user, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new BusinessLogicException("No active subscription found."));

        if (activeMembership.getStripeSubscriptionId() != null) {
            stripeService.cancelSubscription(activeMembership.getStripeSubscriptionId());
            log.info("Subscription cancellation requested in Stripe for user {}.", user.getUsername());
        } else {
            activeMembership.setStatus(MembershipStatus.CANCELLED);
            activeMembership.setEndDate(LocalDateTime.now());
            userMembershipRepository.save(activeMembership);
            log.warn("Cancelled local membership for user {} without a Stripe subscription ID.", user.getUsername());
        }
    }

    @Transactional
    public ProductBoost boostProduct(UserDetails currentUserDetails, Long productId) {
        User user = userRepository.findByUsername(currentUserDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(currentUserDetails.getUsername()));

        UserMembership activeMembership = userMembershipRepository.findByUserAndStatus(user, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new BusinessLogicException("You must have an active membership to boost products."));

        if (activeMembership.getBoostsRemaining() == null || activeMembership.getBoostsRemaining() <= 0) {
            throw new BusinessLogicException("No boosts remaining for this month.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessLogicException("Product not found."));

        if (!product.getSeller().equals(user)) {
            throw new BusinessLogicException("You can only boost your own products.");
        }

        if (productBoostRepository.findByProductAndBoostEndTimeAfter(product, LocalDateTime.now()).isPresent()) {
            throw new BusinessLogicException("Product is already boosted.");
        }

        activeMembership.setBoostsRemaining(activeMembership.getBoostsRemaining() - 1);
        userMembershipRepository.save(activeMembership);

        ProductBoost productBoost = ProductBoost.builder()
                .product(product)
                .user(user)
                .boostEndTime(LocalDateTime.now().plusHours(24))
                .build();

        log.info("User {} boosted product {}. Boosts remaining: {}", user.getUsername(), productId, activeMembership.getBoostsRemaining());
        return productBoostRepository.save(productBoost);
    }

    @Transactional
    public void activateSubscription(String stripeSubscriptionId, Long userId, Long planId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessLogicException("User not found for ID: " + userId));

        UserMembership membership = userMembershipRepository.findFirstByUserAndPlanPlanIdAndStatusOrderByCreatedAtDesc(user, planId, MembershipStatus.PENDING)
                .orElseThrow(() -> {
                    log.error("Could not find PENDING membership for user {} and plan {} to activate.", userId, planId);
                    return new BusinessLogicException("Pending membership not found for activation.");
                });

        MembershipPlan plan = membership.getPlan();

        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setStripeSubscriptionId(stripeSubscriptionId);
        membership.setBoostsRemaining(plan.getBoostsPerMonth());
        membership.setStartDate(LocalDateTime.now());
        membership.setEndDate(LocalDateTime.now().plusMonths(1));

        userMembershipRepository.save(membership);
        log.info("Successfully activated membership for user {} with plan {}.", user.getUsername(), plan.getName());
    }

    @Transactional
    public void renewMembership(String stripeSubscriptionId) {
        UserMembership membership = userMembershipRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow(() -> new BusinessLogicException("Membership not found for subscription ID: " + stripeSubscriptionId));

        membership.setBoostsRemaining(membership.getPlan().getBoostsPerMonth());

        LocalDateTime newEndDate = membership.getEndDate() != null
                ? membership.getEndDate().plusMonths(1)
                : LocalDateTime.now().plusMonths(1);
        membership.setEndDate(newEndDate);

        membership.setStatus(MembershipStatus.ACTIVE);

        userMembershipRepository.save(membership);
        log.info("Successfully renewed membership for subscription {}. New end date: {}", stripeSubscriptionId, newEndDate);
    }

    @Transactional
    public void deactivateSubscription(String stripeSubscriptionId) {
        UserMembership membership = userMembershipRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .orElseThrow(() -> new BusinessLogicException("Membership not found for subscription"));

        membership.setStatus(MembershipStatus.CANCELLED);
        membership.setEndDate(LocalDateTime.now());

        userMembershipRepository.save(membership);
    }
}