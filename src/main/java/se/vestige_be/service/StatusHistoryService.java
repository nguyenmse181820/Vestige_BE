package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.pojo.OrderItem;
import se.vestige_be.pojo.OrderItemStatusHistory;
import se.vestige_be.pojo.enums.OrderItemStatus;
import se.vestige_be.repository.OrderItemStatusHistoryRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatusHistoryService {

    private final OrderItemStatusHistoryRepository historyRepository;

    /**
     * Records a status change for an order item
     */
    @Transactional
    public void recordStatusChange(OrderItem orderItem, OrderItemStatus newStatus, String updatedBy, String notes) {
        log.info("Recording status change for order item {}: {} -> {}", 
                orderItem.getOrderItemId(), orderItem.getStatus(), newStatus);
        
        OrderItemStatusHistory historyEntry = OrderItemStatusHistory.builder()
                .orderItem(orderItem)
                .status(newStatus)
                .changedAt(LocalDateTime.now())
                .updatedBy(updatedBy)
                .notes(notes)
                .build();
        
        historyRepository.save(historyEntry);
        log.debug("Status history recorded for order item {}", orderItem.getOrderItemId());
    }

    /**
     * Get complete status history for an order item
     */
    public List<OrderItemStatusHistory> getStatusHistory(Long orderItemId) {
        return historyRepository.findByOrderItemOrderItemIdOrderByChangedAtDesc(orderItemId);
    }

    /**
     * Get the most recent status change for an order item
     */
    public OrderItemStatusHistory getLatestStatusChange(Long orderItemId) {
        return historyRepository.findLatestByOrderItemId(orderItemId);
    }
}
