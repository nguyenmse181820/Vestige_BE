package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String status; // pending, approved, rejected, expired

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal certificationFee;

    private LocalDateTime approvedAt;
    private LocalDateTime expiresAt;

    @Column(columnDefinition = "json")
    private String verificationDocuments;

    @ManyToOne
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @Column(length = 20, columnDefinition = "varchar(20)")
    private String verificationLevel; // standard, expert, authority

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}