package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import se.vestige_be.pojo.enums.OfferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "offers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long offerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @ToString.Exclude
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    @ToString.Exclude
    private User buyer;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OfferStatus status = OfferStatus.PENDING;

    @Column(columnDefinition = "text")
    private String message;

    private LocalDateTime expiresAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public void accept() {
        if (OfferStatus.PENDING.equals(this.status)) {
            this.status = OfferStatus.ACCEPTED;
        }
    }

    public void reject() {
        if (OfferStatus.PENDING.equals(this.status)) {
            this.status = OfferStatus.REJECTED;
        }
    }

    public boolean isExpired() {
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            if (OfferStatus.PENDING.equals(this.status)) {
                this.status = OfferStatus.EXPIRED;
            }
            return true;
        }
        return false;
    }

    /**
     * Calculate the discount percentage from the product's price
     */
    public BigDecimal getDiscountPercentage() {
        if (product == null || product.getPrice() == null || amount == null) {
            return null;
        }

        if (product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal discount = product.getPrice().subtract(amount);
        return discount.multiply(new BigDecimal("100"))
                .divide(product.getPrice(), 2, BigDecimal.ROUND_HALF_UP);
    }
}