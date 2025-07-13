package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "certification_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certification_id", nullable = false)
    private LegitProfileCertification certification;

    @Column(name = "document_url", nullable = false, length = 512)
    private String documentUrl; // This will store the URL from Firebase

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType; // e.g., 'ID_CARD', 'BUSINESS_LICENSE', 'PROOF_OF_ADDRESS'

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;
}
