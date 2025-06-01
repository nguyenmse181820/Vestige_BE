package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.vestige_be.pojo.OrderItem;
import se.vestige_be.pojo.enums.EscrowStatus;
import se.vestige_be.pojo.enums.OrderItemStatus;
import se.vestige_be.repository.OrderItemRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EscrowCalculationService {

    private final OrderItemRepository orderItemRepository;

    public BigDecimal getSellerPendingEscrowAmount(Long sellerId) {
        List<OrderItem> holdingItems = orderItemRepository.findBySellerUserIdAndEscrowStatus(
                sellerId, EscrowStatus.HOLDING);

        return holdingItems.stream()
                .map(item -> item.getPrice().subtract(item.getPlatformFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getBuyerEscrowAmount(Long buyerId) {
        List<OrderItem> holdingItems = orderItemRepository.findByOrderBuyerUserIdAndEscrowStatus(
                buyerId, EscrowStatus.HOLDING);

        return holdingItems.stream()
                .map(OrderItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getSellerTotalRevenue(Long sellerId) {
        List<OrderItem> deliveredItems = orderItemRepository.findBySellerUserIdAndStatus(
                sellerId, OrderItemStatus.DELIVERED);

        return deliveredItems.stream()
                .map(item -> item.getPrice().subtract(item.getPlatformFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getSellerMonthlyRevenue(Long sellerId) {
        // Get first day of current month
        LocalDateTime startOfMonth = LocalDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfMonth = LocalDateTime.now().with(TemporalAdjusters.lastDayOfMonth()).withHour(23).withMinute(59).withSecond(59);

        List<OrderItem> monthlyDeliveredItems = orderItemRepository.findBySellerUserIdAndStatus(
                sellerId, OrderItemStatus.DELIVERED);

        return monthlyDeliveredItems.stream()
                .filter(item -> {
                    LocalDateTime deliveredAt = item.getOrder().getDeliveredAt();
                    return deliveredAt != null &&
                            deliveredAt.isAfter(startOfMonth) &&
                            deliveredAt.isBefore(endOfMonth);
                })
                .map(item -> item.getPrice().subtract(item.getPlatformFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Buyer statistics
    public BigDecimal getBuyerTotalSpent(Long buyerId) {
        List<OrderItem> deliveredItems = orderItemRepository.findByOrderBuyerUserIdAndStatus(buyerId, OrderItemStatus.DELIVERED);

        return deliveredItems.stream()
                .map(OrderItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getBuyerMonthlySpent(Long buyerId) {
        LocalDateTime startOfMonth = LocalDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfMonth = LocalDateTime.now().with(TemporalAdjusters.lastDayOfMonth()).withHour(23).withMinute(59).withSecond(59);

        List<OrderItem> monthlyItems = orderItemRepository.findByOrderBuyerUserIdAndStatus(buyerId, OrderItemStatus.DELIVERED);

        return monthlyItems.stream()
                .filter(item -> {
                    LocalDateTime deliveredAt = item.getOrder().getDeliveredAt();
                    return deliveredAt != null &&
                            deliveredAt.isAfter(startOfMonth) &&
                            deliveredAt.isBefore(endOfMonth);
                })
                .map(OrderItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getBuyerItemsPurchasedCount(Long buyerId) {
        return orderItemRepository.countByOrderBuyerUserIdAndStatus(buyerId, OrderItemStatus.DELIVERED);
    }
}