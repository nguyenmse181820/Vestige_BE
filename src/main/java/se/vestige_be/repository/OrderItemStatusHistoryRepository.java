package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import se.vestige_be.pojo.OrderItemStatusHistory;

import java.util.List;

@Repository
public interface OrderItemStatusHistoryRepository extends JpaRepository<OrderItemStatusHistory, Long> {
    
    /**
     * Find all status history records for a specific order item, ordered by change time
     */
    List<OrderItemStatusHistory> findByOrderItemOrderItemIdOrderByChangedAtDesc(Long orderItemId);
    
    /**
     * Get the latest status change for an order item
     */
    @Query("SELECT h FROM OrderItemStatusHistory h WHERE h.orderItem.orderItemId = :orderItemId ORDER BY h.changedAt DESC LIMIT 1")
    OrderItemStatusHistory findLatestByOrderItemId(Long orderItemId);
}
