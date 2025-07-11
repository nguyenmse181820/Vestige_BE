package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import se.vestige_be.pojo.enums.TrustTier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "membership_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long planId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "boosts_per_month", nullable = false)
    private Integer boostsPerMonth;

    @Column(name = "stripe_price_id", unique = true)
    private String stripePriceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_trust_tier")
    private TrustTier requiredTrustTier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tier_id")
    @ToString.Exclude
    private FeeTier feeTier;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}