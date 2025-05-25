package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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

    @Column(nullable = false, length = 50, columnDefinition = "varchar(50)")
    private String tierName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal minValue;

    @Column(precision = 10, scale = 2)
    private BigDecimal maxValue;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal baseFeePercentage;

    @Column(precision = 5, scale = 2)
    private BigDecimal legitProfileDiscount = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal membershipDiscount = BigDecimal.ZERO;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
