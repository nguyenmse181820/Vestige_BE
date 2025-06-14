package se.vestige_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.Order;
import se.vestige_be.pojo.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByBuyerUserIdOrderByCreatedAtDesc(Long buyerId, Pageable pageable);
    Page<Order> findByBuyerUserIdAndStatusOrderByCreatedAtDesc(Long buyerId, OrderStatus status, Pageable pageable);
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime timestamp);
}