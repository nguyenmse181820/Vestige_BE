package se.vestige_be.service;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.pojo.EscrowRelease;
import se.vestige_be.pojo.OrderItem;
import se.vestige_be.pojo.Transaction;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.enums.EscrowStatus;
import se.vestige_be.repository.EscrowReleaseRepository;
import se.vestige_be.repository.OrderItemRepository;
import se.vestige_be.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class EscrowService {

    private final EscrowReleaseRepository escrowReleaseRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final EscrowCalculationService escrowCalculationService;

    @Transactional
    public void releaseEscrowFunds(OrderItem orderItem, String reason) {
        log.info("Releasing escrow funds for order item: {}, reason: {}", orderItem.getOrderItemId(), reason);

        // Calculate seller amount (minus platform fee)
        BigDecimal sellerAmount = orderItem.getPrice().subtract(orderItem.getPlatformFee());

        // Update escrow status
        orderItem.setEscrowStatus(EscrowStatus.RELEASED);

        // Create escrow release record
        EscrowRelease escrowRelease = EscrowRelease.builder()
                .transaction(getTransactionForOrderItem(orderItem))
                .amountReleased(sellerAmount)
                .status("COMPLETED")
                .releaseReason(reason)
                .completedAt(LocalDateTime.now())
                .build();

        escrowReleaseRepository.save(escrowRelease);

        // Update seller's successful transaction count
        User seller = orderItem.getSeller();
        seller.setSuccessfulTransactions(seller.getSuccessfulTransactions() + 1);
        userRepository.save(seller);

        log.info("Escrow funds released: {} VND to seller: {}", sellerAmount, seller.getUsername());
    }

    @Transactional
    public void refundEscrowFunds(OrderItem orderItem, String reason) {
        log.info("Refunding escrow funds for order item: {}, reason: {}", orderItem.getOrderItemId(), reason);

        // Update escrow status
        orderItem.setEscrowStatus(EscrowStatus.REFUNDED);

        // Create escrow release record for refund
        EscrowRelease escrowRelease = EscrowRelease.builder()
                .transaction(getTransactionForOrderItem(orderItem))
                .amountReleased(orderItem.getPrice()) // Full refund to buyer
                .status("REFUNDED")
                .releaseReason(reason)
                .completedAt(LocalDateTime.now())
                .build();

        escrowReleaseRepository.save(escrowRelease);

        log.info("Escrow funds refunded: {} VND to buyer: {}", orderItem.getPrice(), orderItem.getOrder().getBuyer().getUsername());
    }

    public BigDecimal calculateEscrowBalance(Long userId, boolean isSeller) {
        if (isSeller) {
            return escrowCalculationService.getSellerPendingEscrowAmount(userId);
        } else {
            return escrowCalculationService.getBuyerEscrowAmount(userId);
        }
    }

    public boolean isEscrowEligible(BigDecimal amount, User buyer, User seller) {
        // Basic eligibility checks
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Escrow not eligible: Invalid amount {}", amount);
            return false;
        }

        // Check if buyer and seller accounts are in good standing
        if (!"active".equals(buyer.getAccountStatus())) {
            log.warn("Escrow not eligible: Buyer account not active: {}", buyer.getUsername());
            return false;
        }

        if (!"active".equals(seller.getAccountStatus())) {
            log.warn("Escrow not eligible: Seller account not active: {}", seller.getUsername());
            return false;
        }

        // Additional business rules can be added here
        return true;
    }

    public EscrowSummary getEscrowSummary(Long userId, boolean isSeller) {
        if (isSeller) {
            BigDecimal pendingAmount = escrowCalculationService.getSellerPendingEscrowAmount(userId);
            BigDecimal totalRevenue = escrowCalculationService.getSellerTotalRevenue(userId);
            Long itemsInEscrow = orderItemRepository.countBySellerUserIdAndEscrowStatus(userId, EscrowStatus.HOLDING);
            Long itemsReleased = orderItemRepository.countBySellerUserIdAndEscrowStatus(userId, EscrowStatus.RELEASED);

            return EscrowSummary.builder()
                    .pendingAmount(pendingAmount)
                    .totalProcessed(totalRevenue)
                    .itemsInEscrow(itemsInEscrow.intValue())
                    .itemsReleased(itemsReleased.intValue())
                    .build();
        } else {
            BigDecimal escrowAmount = escrowCalculationService.getBuyerEscrowAmount(userId);
            BigDecimal totalSpent = escrowCalculationService.getBuyerTotalSpent(userId);
            Long itemsInEscrow = orderItemRepository.countByOrderBuyerUserIdAndEscrowStatus(userId, EscrowStatus.HOLDING);

            return EscrowSummary.builder()
                    .pendingAmount(escrowAmount)
                    .totalProcessed(totalSpent)
                    .itemsInEscrow(itemsInEscrow.intValue())
                    .itemsReleased(0) // Not relevant for buyers
                    .build();
        }
    }

    // Get recent escrow activities
    public List<EscrowRelease> getRecentEscrowActivities(String status, int limit) {
        return escrowReleaseRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .limit(limit)
                .toList();
    }

    public List<EscrowRelease> getEscrowActivitiesByDateRange(LocalDateTime start, LocalDateTime end) {
        return escrowReleaseRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }

    // Helper method to get transaction for order item
    private Transaction getTransactionForOrderItem(OrderItem orderItem) {
        // This would need to be implemented based on your Transaction-OrderItem relationship
        // For now, returning null - you might want to add this relationship
        return null;
    }

    // Inner class for escrow summary
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EscrowSummary {
        private BigDecimal pendingAmount;
        private BigDecimal totalProcessed;
        private Integer itemsInEscrow;
        private Integer itemsReleased;
    }
}