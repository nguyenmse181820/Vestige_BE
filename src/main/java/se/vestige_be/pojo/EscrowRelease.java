package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "escrow_releases")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class EscrowRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long releaseId;

    @ManyToOne
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amountReleased;

    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String status; // pending, completed

    @Column(length = 50, columnDefinition = "varchar(50)")
    private String releaseReason; // delivery_confirmed, dispute_resolved

    private LocalDateTime completedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
