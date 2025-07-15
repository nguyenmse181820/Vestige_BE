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

    List<Transaction> findByEscrowStatus(EscrowStatus escrowStatus);
    
    List<Transaction> findByDisputeStatusNot(se.vestige_be.pojo.enums.DisputeStatus disputeStatus);

    long countByEscrowStatus(EscrowStatus escrowStatus);
    long countByDisputeStatus(se.vestige_be.pojo.enums.DisputeStatus disputeStatus);

    long countBySellerAndDisputeStatusAndCreatedAtAfter(se.vestige_be.pojo.User seller, se.vestige_be.pojo.enums.DisputeStatus disputeStatus, LocalDateTime date);

    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.buyer " +
           "LEFT JOIN FETCH t.seller " +
           "LEFT JOIN FETCH t.orderItem oi " +
           "LEFT JOIN FETCH oi.order " +
           "LEFT JOIN FETCH oi.product p " +
           "LEFT JOIN FETCH p.images " +
           "LEFT JOIN FETCH p.category " +
           "LEFT JOIN FETCH p.brand " +
           "LEFT JOIN FETCH t.reviews r " +
           "WHERE oi.orderItemId = :orderItemId")
    Optional<Transaction> findByOrderItemOrderItemIdWithAllRelationships(@Param("orderItemId") Long orderItemId);
}