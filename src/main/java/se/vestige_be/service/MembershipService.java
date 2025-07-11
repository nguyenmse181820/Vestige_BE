package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.UserMembershipDTO;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.exception.ResourceNotFoundException;
import se.vestige_be.mapper.ModelMapper;
import se.vestige_be.pojo.*;
import se.vestige_be.pojo.enums.MembershipStatus;
import se.vestige_be.repository.*;

import java.math.BigDecimal;
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
    private final TransactionRepository transactionRepository;
    private final ModelMapper modelMapper;
    private final PayOsService payOsService;

    @Transactional(readOnly = true)
    public List<MembershipPlan> getAllPlans() {
        return membershipPlanRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<UserMembershipDTO> getActiveMembership(UserDetails currentUserDetails) {
        User user = userRepository.findByUsername(currentUserDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(currentUserDetails.getUsername()));

        return userMembershipRepository.findByUserAndStatus(user, MembershipStatus.ACTIVE)
                .map(modelMapper::toUserMembershipDTO);
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

        // Create PayOS payment link
        PayOsService.CreatePaymentLinkResult paymentResult = payOsService.createPaymentLink(user, plan);

        // Create pending membership record with order code for tracking
        UserMembership userMembership = UserMembership.builder()
                .user(user)
                .plan(plan)
                .status(MembershipStatus.PENDING)
                .boostsRemaining(0)
                .payosSubscriptionId(paymentResult.getOrderCode()) // Temporary storage for order code
                .build();
        userMembershipRepository.save(userMembership);

        log.info("Created pending membership for user {} with order code: {}", 
                user.getUsername(), paymentResult.getOrderCode());

        // Return the checkout URL for the frontend to redirect to
        return paymentResult.getCheckoutUrl();
    }

    @Transactional
    public void cancelSubscription(UserDetails currentUserDetails) {
        User user = userRepository.findByUsername(currentUserDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(currentUserDetails.getUsername()));

        UserMembership activeMembership = userMembershipRepository.findByUserAndStatus(user, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new BusinessLogicException("No active subscription found."));

        // PayOS subscriptions are handled differently - no need to cancel remotely
        // Just update the local membership status
        activeMembership.setStatus(MembershipStatus.CANCELLED);
        activeMembership.setEndDate(LocalDateTime.now());
        userMembershipRepository.save(activeMembership);
        
        log.info("Cancelled membership for user {}.", user.getUsername());
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

    /**
     * Activates subscription when PayOS payment is successful
     * Called by PayOsController webhook handler
     */
    @Transactional
    public void activateSubscription(String orderCode) {
        // Find the pending membership by order code (temporarily stored in payosSubscriptionId)
        UserMembership membership = userMembershipRepository.findByPayosSubscriptionId(orderCode)
                .orElseThrow(() -> {
                    log.error("Could not find PENDING membership for order code: {}", orderCode);
                    return new BusinessLogicException("Pending membership not found for order code: " + orderCode);
                });

        if (membership.getStatus() != MembershipStatus.PENDING) {
            log.warn("Attempting to activate membership that is not in PENDING status: {} for order code: {}", 
                    membership.getStatus(), orderCode);
            return;
        }

        MembershipPlan plan = membership.getPlan();
        User user = membership.getUser();

        // Update membership details
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setPayosSubscriptionId(orderCode); // Keep the order code as subscription ID
        membership.setBoostsRemaining(plan.getBoostsPerMonth());
        membership.setStartDate(LocalDateTime.now());
        membership.setEndDate(LocalDateTime.now().plusMonths(1));

        userMembershipRepository.save(membership);

        // Create transaction record for this payment
        Transaction transaction = Transaction.builder()
                .seller(null) // No seller for membership payments
                .buyer(user)
                .amount(plan.getPrice())
                .platformFee(BigDecimal.ZERO) // No platform fee for membership payments
                .feePercentage(BigDecimal.ZERO)
                .payosOrderCode(orderCode)
                .paidAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);

        log.info("Successfully activated membership for user {} with plan {} and order code: {}", 
                user.getUsername(), plan.getName(), orderCode);
    }

    /**
     * Handles failed PayOS payments
     * Called by PayOsController webhook handler
     */
    @Transactional
    public void handleFailedPayment(String orderCode) {
        Optional<UserMembership> membershipOpt = userMembershipRepository.findByPayosSubscriptionId(orderCode);
        
        if (membershipOpt.isPresent()) {
            UserMembership membership = membershipOpt.get();
            membership.setStatus(MembershipStatus.CANCELLED);
            membership.setEndDate(LocalDateTime.now());
            userMembershipRepository.save(membership);
            
            log.info("Marked membership as cancelled for failed payment with order code: {}", orderCode);
        } else {
            log.warn("No membership found for failed payment with order code: {}", orderCode);
        }
    }
}