package se.vestige_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.Order;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.orderItems oi " +
           "LEFT JOIN FETCH oi.product p " +
           "LEFT JOIN FETCH oi.seller " +
           "WHERE o.buyer.userId = :buyerId " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findByBuyerUserIdOrderByCreatedAtDesc(@Param("buyerId") Long buyerId, Pageable pageable);
    
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.orderItems oi " +
           "LEFT JOIN FETCH oi.product p " +
           "LEFT JOIN FETCH oi.seller " +
           "WHERE o.buyer.userId = :buyerId AND o.status = :status " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findByBuyerUserIdAndStatusOrderByCreatedAtDesc(@Param("buyerId") Long buyerId, @Param("status") OrderStatus status, Pageable pageable);
    
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime timestamp);
    
    long countByStatus(OrderStatus status);
    long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    List<Order> findByStatus(OrderStatus status);

    List<Order> findByBuyerAndCreatedAtAfter(User buyer, LocalDateTime createdAfter);
    
    // Add custom methods for user statistics
    long countByBuyerUserId(Long buyerId);
    long countByBuyerUserIdAndStatus(Long buyerId, OrderStatus status);
    
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.buyer.userId = :buyerId")
    BigDecimal sumTotalAmountByBuyerUserId(@Param("buyerId") Long buyerId);
    
    @Query("SELECT MAX(o.createdAt) FROM Order o WHERE o.buyer.userId = :buyerId")
    LocalDateTime findLastOrderDateByBuyerUserId(@Param("buyerId") Long buyerId);
    
    // Methods needed for admin user order summaries
    List<Order> findByBuyerUserId(Long buyerId);
    
    // Methods needed for comprehensive statistics
    List<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
}