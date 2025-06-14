package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.Transaction;
import se.vestige_be.pojo.enums.EscrowStatus;
import se.vestige_be.pojo.enums.TransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByOrderItemOrderItemId(Long orderItemId);
    Optional<Transaction> findFirstByStripePaymentIntentId(String paymentIntentId);
    List<Transaction> findByStatusAndDeliveredAtBeforeAndEscrowStatus(
            TransactionStatus status,
            LocalDateTime deliveredBefore,
            EscrowStatus escrowStatus
    );
}