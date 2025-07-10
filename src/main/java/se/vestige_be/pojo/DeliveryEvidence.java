package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_evidence")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(name = "image_url", nullable = false, length = 512)
    private String imageUrl;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
