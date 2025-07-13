package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.vestige_be.pojo.CertificationDocument;

import java.util.List;

@Repository
public interface CertificationDocumentRepository extends JpaRepository<CertificationDocument, Long> {
    
    List<CertificationDocument> findByCertificationCertificationId(Long certificationId);
    
    void deleteByCertificationCertificationId(Long certificationId);
}
