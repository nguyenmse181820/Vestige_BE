package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_authenticity_evidence")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductAuthenticityEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long evidenceId;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = 50, columnDefinition = "varchar(50)")
    private String evidenceType; // receipt, certificate, etc.

    @Column(length = 255, columnDefinition = "varchar(255)")
    private String evidenceUrl;

    @Column(columnDefinition = "text")
    private String description;

    private Boolean verifiedByAdmin = false;

    @Column(columnDefinition = "text")
    private String adminComments;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
