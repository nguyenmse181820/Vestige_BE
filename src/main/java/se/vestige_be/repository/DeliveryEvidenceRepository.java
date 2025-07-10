package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.DeliveryEvidence;

public interface DeliveryEvidenceRepository extends JpaRepository<DeliveryEvidence, Long> {
}
