package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import se.vestige_be.pojo.enums.EvidenceType;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_authenticity_evidence")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAuthenticityEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long evidenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @ToString.Exclude
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EvidenceType evidenceType;

    @Column(length = 255)
    private String evidenceUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Builder.Default
    private Boolean verifiedByAdmin = false;

    @Column(columnDefinition = "text")
    private String adminComments;

    @CreationTimestamp
    private LocalDateTime createdAt;
}