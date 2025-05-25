package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
