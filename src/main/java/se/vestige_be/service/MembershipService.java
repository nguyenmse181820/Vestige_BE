package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.UserMembershipDTO;
import se.vestige_be.dto.response.UserSubscriptionStatusResponse;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.exception.ResourceNotFoundException;
import se.vestige_be.mapper.ModelMapper;
import se.vestige_be.pojo.*;
import se.vestige_be.pojo.enums.EscrowStatus;
import se.vestige_be.pojo.enums.MembershipStatus;
import se.vestige_be.pojo.enums.TransactionStatus;
import se.vestige_be.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        MembershipPlan plan = membershipPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessLogicException("Membership plan not found."));

        Optional<UserMembership> activeMembershipOpt = userMembershipRepository.findByUserAndStatus(user, MembershipStatus.ACTIVE);

        if (activeMembershipOpt.isPresent()) {
            UserMembership activeMembership = activeMembershipOpt.get();
            MembershipPlan oldPlan = activeMembership.getPlan();

            // SCENARIO 1: EXTENSION (User buys the same plan again)
            if (plan.getPlanId().equals(oldPlan.getPlanId())) {
                log.info("User {} is extending their current plan: {}", user.getUsername(), plan.getName());
                
                // NOTE: We do NOT touch the activeMembership here. It remains ACTIVE.
                // Create a new record to track the extension payment
                PayOsService.CreatePaymentLinkResult paymentResult = payOsService.createPaymentLink(user, plan);
                
                UserMembership extensionMembership = UserMembership.builder()
                        .user(user)
                        .plan(plan)
                        .status(MembershipStatus.PENDING_EXTEND) // Awaiting payment
                        .boostsRemaining(plan.getBoostsPerMonth())
                        .payosSubscriptionId(paymentResult.getOrderCode())
                        .build();
                
                userMembershipRepository.save(extensionMembership);
                
                log.info("Created PENDING_EXTEND membership for user {} with order code: {}", 
                        user.getUsername(), paymentResult.getOrderCode());
                
                return paymentResult.getCheckoutUrl(); // Return URL, leaving the active plan untouched
            }
            // SCENARIO 2: UPGRADE (User buys a more expensive plan)
            else if (plan.getPrice().compareTo(oldPlan.getPrice()) > 0) {
                log.info("User {} is upgrading from {} to {}", user.getUsername(), oldPlan.getName(), plan.getName());
                
                activeMembership.setStatus(MembershipStatus.CANCELLED);
                activeMembership.setEndDate(LocalDateTime.now());
                userMembershipRepository.save(activeMembership);
                
                log.info("Cancelled existing membership for user {} to upgrade from {} to {}", 
                        user.getUsername(), oldPlan.getName(), plan.getName());
            }
            // SCENARIO 3: DOWNGRADE (Not allowed)
            else {
                throw new BusinessLogicException("Downgrading or subscribing to a cheaper plan is not allowed. Please manage your current subscription.");
            }
        }

        if (plan.getRequiredTrustTier() != null) {
            if (user.getTrustTier() == null || user.getTrustTier().getLevel() < plan.getRequiredTrustTier().getLevel()) {
                throw new BusinessLogicException("You do not meet the required Trust Tier for this plan. Required: " + plan.getRequiredTrustTier());
            }
        }

        PayOsService.CreatePaymentLinkResult paymentResult = payOsService.createPaymentLink(user, plan);

        UserMembership userMembership = UserMembership.builder()
                .user(user)
                .plan(plan)
                .status(MembershipStatus.PENDING)
                .boostsRemaining(0)
                .payosSubscriptionId(paymentResult.getOrderCode())
                .build();
        userMembershipRepository.save(userMembership);

        log.info("Created pending membership for user {} with order code: {}", 
                user.getUsername(), paymentResult.getOrderCode());

        return paymentResult.getCheckoutUrl();
    }

    @Transactional
    public void cancelSubscription(UserDetails currentUserDetails) {
        User user = userRepository.findByUsername(currentUserDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(currentUserDetails.getUsername()));

        UserMembership activeMembership = userMembershipRepository.findByUserAndStatus(user, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new BusinessLogicException("No active subscription found."));
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

    @Transactional
    public void activateSubscription(String orderCode) {
        Optional<UserMembership> membershipOpt = userMembershipRepository.findByPayosSubscriptionId(orderCode);
        
        if (membershipOpt.isEmpty()) {
            String paddedOrderCode = String.format("%08d", Long.parseLong(orderCode));
            membershipOpt = userMembershipRepository.findByPayosSubscriptionId(paddedOrderCode);
            
            if (membershipOpt.isPresent()) {
                log.info("Found membership with padded order code: {} (original: {})", paddedOrderCode, orderCode);
            }
        }
        
        if (membershipOpt.isEmpty()) {
            log.error("Could not find PENDING membership for order code: {} (also tried with leading zeros). Checking all memberships...", orderCode);
            
            // Debug: List all pending memberships
            List<UserMembership> allPendingMemberships = userMembershipRepository.findAll().stream()
                    .filter(m -> m.getStatus() == MembershipStatus.PENDING || m.getStatus() == MembershipStatus.PENDING_EXTEND)
                    .toList();

            log.info("Found {} pending memberships:", allPendingMemberships.size());
            allPendingMemberships.forEach(m ->
                log.info("  - ID: {}, User: {}, OrderCode: {}, Status: {}",
                        m.getMembershipId(), m.getUser().getUsername(),
                        m.getPayosSubscriptionId(), m.getStatus()));
            
            throw new BusinessLogicException("Pending membership not found for order code: " + orderCode);
        }
        
        UserMembership membership = membershipOpt.get();

        if (membership.getStatus() == MembershipStatus.PENDING) {
            MembershipPlan plan = membership.getPlan();
            User user = membership.getUser();

            // Update membership details
            try {
                membership.setStatus(MembershipStatus.ACTIVE);
                membership.setPayosSubscriptionId(orderCode); // Keep the order code as subscription ID
                membership.setBoostsRemaining(plan.getBoostsPerMonth());
                membership.setStartDate(LocalDateTime.now());
                membership.setEndDate(LocalDateTime.now().plusMonths(1));

                log.info("Saving membership with ID: {}, Status: {}, PayOS ID: {}", 
                        membership.getMembershipId(), membership.getStatus(), membership.getPayosSubscriptionId());
                
                userMembershipRepository.save(membership);
                log.info("Membership updated successfully in database");
                
            } catch (Exception dbException) {
                log.error("Database error during membership update: {}", dbException.getMessage(), dbException);
                throw new RuntimeException("Database error during membership update: " + dbException.getMessage(), dbException);
            }

            // Create transaction record for this payment
            try {
                log.info("Creating transaction record for membership payment");
                Transaction transaction = Transaction.builder()
                        .seller(null) // No seller for membership payments
                        .buyer(user)
                        .amount(plan.getPrice())
                        .platformFee(BigDecimal.ZERO) // No platform fee for membership payments
                        .feePercentage(BigDecimal.ZERO)
                        .status(TransactionStatus.PAID) // Membership payment is already verified as paid
                        .escrowStatus(EscrowStatus.CANCELLED) // No escrow needed for membership payments
                        .payosOrderCode(orderCode)
                        .paidAt(LocalDateTime.now())
                        .build();

                Transaction savedTransaction = transactionRepository.save(transaction);
                log.info("Transaction created successfully with ID: {}", savedTransaction.getTransactionId());
                
            } catch (Exception transactionException) {
                log.error("Database error during transaction creation: {}", transactionException.getMessage(), transactionException);
                throw new RuntimeException("Database error during transaction creation: " + transactionException.getMessage(), transactionException);
            }
        }
        else if (membership.getStatus() == MembershipStatus.PENDING_EXTEND) {
            User user = membership.getUser();
            MembershipPlan plan = membership.getPlan();

            // Find the active membership (current plan)
            UserMembership activeMembership = userMembershipRepository
                    .findByUserAndStatus(user, MembershipStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessLogicException("Cannot find an active plan to extend."));

            // Check if there are any existing queued plans
            List<UserMembership> queuedPlans = userMembershipRepository.findQueuedPlansByUser(user);

            // Determine the start date for the new queued plan
            LocalDateTime newStartDate;
            if (queuedPlans.isEmpty()) {
                // No queued plans exist, start after the active plan ends
                newStartDate = activeMembership.getEndDate();
                log.info("No queued plans found for user {}. New plan will start after active plan ends: {}", 
                        user.getUsername(), newStartDate);
            } else {
                // Since queuedPlans are ordered by end date descending, the first one has the latest end date
                newStartDate = queuedPlans.get(0).getEndDate();
                log.info("Found {} queued plans for user {}. New plan will start after latest queued plan ends: {}", 
                        queuedPlans.size(), user.getUsername(), newStartDate);
            }

            // Set the membership details
            membership.setStatus(MembershipStatus.QUEUED);
            membership.setPayosSubscriptionId(orderCode);
            membership.setBoostsRemaining(plan.getBoostsPerMonth());
            membership.setStartDate(newStartDate);
            membership.setEndDate(newStartDate.plusMonths(1)); // Default to 1 month duration
            
            userMembershipRepository.save(membership);
            
            log.info("Extension membership queued with ID: {}, will start: {}, will end: {}", 
                    membership.getMembershipId(), membership.getStartDate(), membership.getEndDate());

            try {
                Transaction transaction = Transaction.builder()
                        .seller(null)
                        .buyer(user)
                        .amount(plan.getPrice())
                        .platformFee(BigDecimal.ZERO)
                        .feePercentage(BigDecimal.ZERO)
                        .status(TransactionStatus.PAID)
                        .escrowStatus(EscrowStatus.CANCELLED)
                        .payosOrderCode(orderCode)
                        .paidAt(LocalDateTime.now())
                        .build();

                Transaction savedTransaction = transactionRepository.save(transaction);
                log.info("Extension transaction created successfully with ID: {}", savedTransaction.getTransactionId());
                
            } catch (Exception transactionException) {
                log.error("Database error during extension transaction creation: {}", transactionException.getMessage(), transactionException);
                throw new RuntimeException("Database error during extension transaction creation: " + transactionException.getMessage(), transactionException);
            }
        }
        else {
            log.warn("Membership with order code {} has unexpected status: {}", orderCode, membership.getStatus());
            return;
        }
    }

    @Transactional(readOnly = true)
    public UserSubscriptionStatusResponse getFullSubscriptionStatus(UserDetails currentUserDetails) {
        User user = userRepository.findByUsername(currentUserDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(currentUserDetails.getUsername()));

        Optional<UserMembership> activeMembershipOpt = userMembershipRepository.findByUserAndStatus(user, MembershipStatus.ACTIVE);
        List<UserMembership> queuedMemberships = userMembershipRepository.findQueuedPlansByUser(user);

        UserMembershipDTO activeDto = activeMembershipOpt.map(modelMapper::toUserMembershipDTO).orElse(null);
        List<UserMembershipDTO> queuedDtos = queuedMemberships.stream()
                .map(modelMapper::toUserMembershipDTO)
                .collect(Collectors.toList());

        LocalDateTime finalExpirationDate = null;
        if (!queuedDtos.isEmpty()) {
            // Since queuedDtos are ordered by endDate DESC, the first one has the latest end date
            finalExpirationDate = queuedDtos.get(0).getEndDate();
        } else if (activeDto != null) {
            finalExpirationDate = activeDto.getEndDate();
        }

        int totalBoosts = Stream.concat(
                activeMembershipOpt.stream(),
                queuedMemberships.stream()
        ).mapToInt(m -> m.getBoostsRemaining() != null ? m.getBoostsRemaining() : 0).sum();


        return UserSubscriptionStatusResponse.builder()
                .activeMembership(activeDto)
                .queuedMemberships(queuedDtos)
                .finalExpirationDate(finalExpirationDate)
                .totalBoostsAvailable(totalBoosts)
                .build();
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
            
            // Only cancel if the membership is in a pending state
            if (membership.getStatus() == MembershipStatus.PENDING || 
                membership.getStatus() == MembershipStatus.PENDING_EXTEND) {
                
                membership.setStatus(MembershipStatus.CANCELLED);
                membership.setEndDate(LocalDateTime.now());
                userMembershipRepository.save(membership);
                
                log.info("Cancelled membership due to failed payment. Order code: {}, Status was: {}", 
                        orderCode, membership.getStatus());
            } else {
                log.warn("Payment failed for order code: {}, but membership status is: {} - not cancelling", 
                        orderCode, membership.getStatus());
            }
        } else {
            log.warn("No membership found for failed payment with order code: {}", orderCode);
        }
    }

    /**
     * Cancel a pending subscription (used when PayOS payment is cancelled)
     */
    @Transactional
    public void cancelPendingSubscription(String orderCode) {
        log.info("Cancelling pending subscription for order code: {}", orderCode);
        
        Optional<UserMembership> membershipOpt = userMembershipRepository.findByPayosSubscriptionId(orderCode);
        
        if (membershipOpt.isEmpty()) {
            String paddedOrderCode = String.format("%08d", Long.parseLong(orderCode));
            membershipOpt = userMembershipRepository.findByPayosSubscriptionId(paddedOrderCode);
        }
        
        if (membershipOpt.isPresent()) {
            UserMembership membership = membershipOpt.get();
            
            // Only cancel if the membership is in a pending state
            if (membership.getStatus() == MembershipStatus.PENDING || 
                membership.getStatus() == MembershipStatus.PENDING_EXTEND) {
                
                membership.setStatus(MembershipStatus.CANCELLED);
                membership.setEndDate(LocalDateTime.now());
                userMembershipRepository.save(membership);
                
                log.info("Successfully cancelled pending subscription for order code: {}", orderCode);
            } else {
                log.warn("Subscription for order code: {} is not in pending state (status: {})", 
                        orderCode, membership.getStatus());
                throw new BusinessLogicException("Subscription is not in a cancellable state");
            }
        } else {
            log.warn("No pending subscription found for order code: {}", orderCode);
            throw new BusinessLogicException("Pending subscription not found for order code: " + orderCode);
        }
    }
}