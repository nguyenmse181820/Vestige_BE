package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.Transaction;
import se.vestige_be.pojo.enums.EscrowStatus;
import se.vestige_be.pojo.enums.TransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    Optional<Transaction> findByOrderItemOrderItemId(Long orderItemId);
    Optional<Transaction> findFirstByStripePaymentIntentId(String paymentIntentId);
    List<Transaction> findByStatusAndDeliveredAtBeforeAndEscrowStatus(
            TransactionStatus status,
            LocalDateTime deliveredBefore,
            EscrowStatus escrowStatus
    );
    List<Transaction> findByDisputeStatus(se.vestige_be.pojo.enums.DisputeStatus disputeStatus);
    List<Transaction> findByEscrowStatus(EscrowStatus escrowStatus);
    List<Transaction> findByEscrowStatusAndCreatedAtBefore(EscrowStatus escrowStatus, LocalDateTime createdBefore);
    List<Transaction> findBySellerAndCreatedAtAfter(se.vestige_be.pojo.User seller, LocalDateTime createdAfter);
    
    // Custom query to fetch transactions with all relationships loaded
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.buyer " +
           "LEFT JOIN FETCH t.seller " +
           "LEFT JOIN FETCH t.orderItem oi " +
           "LEFT JOIN FETCH oi.order " +
           "LEFT JOIN FETCH oi.product " +
           "WHERE t.transactionId IN :transactionIds")
    List<Transaction> findByTransactionIdInWithAllRelationships(@Param("transactionIds") List<Long> transactionIds);
    
    List<Transaction> findByDisputeStatusNot(se.vestige_be.pojo.enums.DisputeStatus disputeStatus);

    // Count methods for statistics
    long countByEscrowStatus(EscrowStatus escrowStatus);
    long countByDisputeStatus(se.vestige_be.pojo.enums.DisputeStatus disputeStatus);
}