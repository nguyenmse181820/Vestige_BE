package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.vestige_be.pojo.PickupEvidence;

import java.util.List;

@Repository
public interface PickupEvidenceRepository extends JpaRepository<PickupEvidence, Long> {
    
    /**
     * Find all pickup evidence records for a specific transaction
     */
    List<PickupEvidence> findByTransaction_TransactionId(Long transactionId);
    
    /**
     * Count the number of evidence photos for a transaction
     */
    long countByTransaction_TransactionId(Long transactionId);
}
