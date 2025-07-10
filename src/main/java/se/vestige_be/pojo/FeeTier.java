package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_tiers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tierId;

    @Column(nullable = false, unique = true, length = 50, columnDefinition = "varchar(50)")
    private String name;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal feePercentage;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
