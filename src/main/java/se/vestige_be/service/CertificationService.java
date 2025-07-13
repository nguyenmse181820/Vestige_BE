package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.request.DocumentRequestDto;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.pojo.CertificationDocument;
import se.vestige_be.pojo.LegitProfileCertification;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.enums.CertificationStatus;
import se.vestige_be.repository.LegitProfileCertificationRepository;
import se.vestige_be.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificationService {

    private final LegitProfileCertificationRepository certificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public LegitProfileCertification submitCertification(Long userId, List<DocumentRequestDto> documentRequests) {
        log.info("Processing certification submission for user ID: {}", userId);
        
        // 1. Fetch the user submitting the request
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessLogicException("User with ID " + userId + " not found."));

        // 2. Check if user already has a pending certification
        if (certificationRepository.existsByUserUserIdAndStatus(userId, CertificationStatus.PENDING)) {
            throw new BusinessLogicException("User already has a pending certification request.");
        }

        // 3. Check if user already has an approved certification
        if (certificationRepository.existsByUserUserIdAndStatus(userId, CertificationStatus.APPROVED)) {
            throw new BusinessLogicException("User already has an approved certification.");
        }

        // 4. Validate document requests
        if (documentRequests == null || documentRequests.isEmpty()) {
            throw new BusinessLogicException("At least one document is required for certification.");
        }

        // 5. Create the parent certification request object
        LegitProfileCertification certification = LegitProfileCertification.builder()
                .user(user)
                .status(CertificationStatus.PENDING)
                .certificationFee(java.math.BigDecimal.ZERO) // Set appropriate fee
                .build();

        // 6. Map the DTOs to CertificationDocument entities
        List<CertificationDocument> documents = documentRequests.stream()
                .map(dto -> CertificationDocument.builder()
                        .certification(certification) // Link back to the parent
                        .documentUrl(dto.getUrl())
                        .documentType(dto.getType())
                        .build())
                .collect(Collectors.toList());

        // 7. Set the document list on the certification object
        certification.setDocuments(documents);

        // 8. Save the parent object. Thanks to CascadeType.ALL, the documents will be saved automatically.
        LegitProfileCertification savedCertification = certificationRepository.save(certification);
        
        log.info("Successfully submitted certification request with ID: {} for user ID: {}", 
                savedCertification.getCertificationId(), userId);
        
        return savedCertification;
    }

    @Transactional(readOnly = true)
    public LegitProfileCertification getCertificationByUserId(Long userId) {
        return certificationRepository.findByUserUserId(userId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<LegitProfileCertification> getAllCertifications(Pageable pageable) {
        return certificationRepository.findAllWithDocuments(pageable);
    }

    @Transactional(readOnly = true)
    public List<LegitProfileCertification> getCertificationsByStatus(CertificationStatus status) {
        return certificationRepository.findByStatus(status);
    }

    @Transactional
    public LegitProfileCertification approveCertification(Long certificationId, Long reviewerId, int expirationMonths) {
        log.info("Processing approval for certification ID: {} by reviewer ID: {}", certificationId, reviewerId);
        
        LegitProfileCertification certification = certificationRepository.findByIdWithDocuments(certificationId)
                .orElseThrow(() -> new BusinessLogicException("Certification not found with ID: " + certificationId));

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new BusinessLogicException("Reviewer not found with ID: " + reviewerId));

        if (certification.getStatus() != CertificationStatus.PENDING) {
            throw new BusinessLogicException("Only pending certifications can be approved.");
        }

        // Use the existing approve method from the entity
        certification.approve(reviewer, expirationMonths);
        
        LegitProfileCertification approvedCertification = certificationRepository.save(certification);
        
        log.info("Successfully approved certification ID: {} for user ID: {}", 
                certificationId, certification.getUser().getUserId());
        
        return approvedCertification;
    }

    @Transactional
    public LegitProfileCertification rejectCertification(Long certificationId, Long reviewerId, String reason) {
        log.info("Processing rejection for certification ID: {} by reviewer ID: {}", certificationId, reviewerId);
        
        LegitProfileCertification certification = certificationRepository.findByIdWithDocuments(certificationId)
                .orElseThrow(() -> new BusinessLogicException("Certification not found with ID: " + certificationId));

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new BusinessLogicException("Reviewer not found with ID: " + reviewerId));

        if (certification.getStatus() != CertificationStatus.PENDING) {
            throw new BusinessLogicException("Only pending certifications can be rejected.");
        }

        // Use the existing reject method from the entity
        certification.reject(reviewer, reason);
        
        LegitProfileCertification rejectedCertification = certificationRepository.save(certification);
        
        log.info("Successfully rejected certification ID: {} for user ID: {}", 
                certificationId, certification.getUser().getUserId());
        
        return rejectedCertification;
    }
}
