package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import se.vestige_be.pojo.enums.CertificationStatus;
import se.vestige_be.pojo.enums.VerificationLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "legit_profile_certifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegitProfileCertification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long certificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CertificationStatus status = CertificationStatus.PENDING;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal certificationFee;

    private LocalDateTime approvedAt;
    private LocalDateTime expiresAt;

    @OneToMany(mappedBy = "certification", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CertificationDocument> documents;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    @ToString.Exclude
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private VerificationLevel verificationLevel = VerificationLevel.STANDARD;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void approve(User reviewer, int expirationMonths) {
        this.status = CertificationStatus.APPROVED;
        this.reviewer = reviewer;
        this.approvedAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusMonths(expirationMonths);

        if (user != null) {
            user.setIsVerified(true);
        }
    }

    public void reject(User reviewer, String reason) {
        this.status = CertificationStatus.REJECTED;
        this.reviewer = reviewer;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public void upgradeLevel(VerificationLevel newLevel) {
        if (CertificationStatus.APPROVED.equals(this.status)) {
            this.verificationLevel = newLevel;
        }
    }
}