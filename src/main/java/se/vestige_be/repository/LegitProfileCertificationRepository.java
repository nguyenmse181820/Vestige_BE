package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import se.vestige_be.pojo.LegitProfileCertification;
import se.vestige_be.pojo.enums.CertificationStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface LegitProfileCertificationRepository extends JpaRepository<LegitProfileCertification, Long> {
    
    @Query("SELECT c FROM LegitProfileCertification c " +
           "LEFT JOIN FETCH c.documents " +
           "LEFT JOIN FETCH c.user " +
           "LEFT JOIN FETCH c.reviewer " +
           "WHERE c.user.userId = :userId")
    List<LegitProfileCertification> findByUserUserId(@Param("userId") Long userId);
    
    @Query("SELECT c FROM LegitProfileCertification c " +
           "LEFT JOIN FETCH c.documents " +
           "LEFT JOIN FETCH c.user " +
           "LEFT JOIN FETCH c.reviewer " +
           "WHERE c.status = :status")
    List<LegitProfileCertification> findByStatus(@Param("status") CertificationStatus status);
    
    @Query("SELECT c FROM LegitProfileCertification c " +
           "LEFT JOIN FETCH c.documents " +
           "LEFT JOIN FETCH c.user " +
           "LEFT JOIN FETCH c.reviewer " +
           "WHERE c.user.userId = :userId AND c.status = :status")
    Optional<LegitProfileCertification> findByUserUserIdAndStatus(@Param("userId") Long userId, @Param("status") CertificationStatus status);
    
    boolean existsByUserUserIdAndStatus(Long userId, CertificationStatus status);
    
    @Query("SELECT c FROM LegitProfileCertification c " +
           "LEFT JOIN FETCH c.documents " +
           "LEFT JOIN FETCH c.user " +
           "LEFT JOIN FETCH c.reviewer " +
           "WHERE c.certificationId = :id")
    Optional<LegitProfileCertification> findByIdWithDocuments(@Param("id") Long id);
    
    @Query(value = "SELECT c FROM LegitProfileCertification c " +
                   "LEFT JOIN FETCH c.documents " +
                   "LEFT JOIN FETCH c.user " +
                   "LEFT JOIN FETCH c.reviewer", 
           countQuery = "SELECT COUNT(c) FROM LegitProfileCertification c")
    org.springframework.data.domain.Page<LegitProfileCertification> findAllWithDocuments(org.springframework.data.domain.Pageable pageable);
}
