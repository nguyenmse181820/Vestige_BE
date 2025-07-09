package se.vestige_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.OrderItem;
import se.vestige_be.pojo.enums.OrderItemStatus;
import se.vestige_be.pojo.enums.EscrowStatus;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findBySellerUserIdAndStatus(Long sellerId, OrderItemStatus status);
    
    // Methods with product and seller loaded, but images loaded separately to avoid MultipleBagFetchException
    @Query("SELECT oi FROM OrderItem oi " +
           "LEFT JOIN FETCH oi.product p " +
           "LEFT JOIN FETCH oi.seller " +
           "LEFT JOIN FETCH oi.order " +
           "WHERE oi.seller.userId = :sellerId " +
           "ORDER BY oi.order.createdAt DESC")
    List<OrderItem> findBySellerUserIdOrderByOrderCreatedAtDesc(@Param("sellerId") Long sellerId);

    @Query("SELECT oi FROM OrderItem oi " +
           "LEFT JOIN FETCH oi.product p " +
           "LEFT JOIN FETCH oi.seller " +
           "LEFT JOIN FETCH oi.order " +
           "WHERE oi.seller.userId = :sellerId " +
           "ORDER BY oi.order.createdAt DESC")
    Page<OrderItem> findBySellerUserIdOrderByOrderCreatedAtDesc(@Param("sellerId") Long sellerId, Pageable pageable);

    @Query("SELECT oi FROM OrderItem oi " +
           "LEFT JOIN FETCH oi.product p " +
           "LEFT JOIN FETCH oi.seller " +
           "LEFT JOIN FETCH oi.order " +
           "WHERE oi.seller.userId = :sellerId AND oi.status = :status " +
           "ORDER BY oi.order.createdAt DESC")
    Page<OrderItem> findBySellerUserIdAndStatusOrderByOrderCreatedAtDesc(@Param("sellerId") Long sellerId, @Param("status") OrderItemStatus status, Pageable pageable);

    // Find order items by seller and escrow status
    List<OrderItem> findBySellerUserIdAndEscrowStatus(Long sellerId, EscrowStatus escrowStatus);
    Long countBySellerUserIdAndStatus(Long sellerId, OrderItemStatus status);
    Long countBySellerUserIdAndEscrowStatus(Long sellerId, EscrowStatus escrowStatus);    List<OrderItem> findByOrderBuyerUserIdAndStatus(Long buyerId, OrderItemStatus status);

    List<OrderItem> findByOrderBuyerUserIdAndEscrowStatus(Long buyerId, EscrowStatus escrowStatus);
    Long countByOrderBuyerUserIdAndStatus(Long buyerId, OrderItemStatus status);
    Long countByOrderBuyerUserIdAndEscrowStatus(Long buyerId, EscrowStatus escrowStatus);
    
    // Admin methods for statistics
    List<OrderItem> findByStatus(OrderItemStatus status);
}