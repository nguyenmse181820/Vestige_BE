package se.vestige_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import se.vestige_be.pojo.enums.CertificationStatus;
import se.vestige_be.pojo.enums.VerificationLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificationResponse {
    
    private Long certificationId;
    private Long userId;
    private String username;
    private CertificationStatus status;
    private BigDecimal certificationFee;
    private LocalDateTime approvedAt;
    private LocalDateTime expiresAt;
    private Long reviewerId;
    private String reviewerUsername;
    private VerificationLevel verificationLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DocumentResponse> documents;
}
