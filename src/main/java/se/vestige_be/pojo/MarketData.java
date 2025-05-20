package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import se.vestige_be.pojo.enums.PriceTrend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dataId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    @ToString.Exclude
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @ToString.Exclude
    private Category category;

    @Column(precision = 10, scale = 2)
    private BigDecimal averagePrice;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PriceTrend priceTrend;

    private Integer totalSales30d;
    private Integer activeListings;

    private LocalDate dataDate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public BigDecimal calculatePriceChangePercent(MarketData previousData) {
        if (previousData == null || previousData.getAveragePrice() == null ||
                this.averagePrice == null || previousData.getAveragePrice().equals(BigDecimal.ZERO)) {
            return null;
        }

        BigDecimal difference = this.averagePrice.subtract(previousData.getAveragePrice());
        return difference.multiply(new BigDecimal("100")).divide(previousData.getAveragePrice(), 2, BigDecimal.ROUND_HALF_UP);
    }

    public void determinePriceTrend(MarketData previousData) {
        BigDecimal changePercent = calculatePriceChangePercent(previousData);

        if (changePercent == null) {
            this.priceTrend = PriceTrend.STABLE;
            return;
        }

        if (changePercent.compareTo(new BigDecimal("1.0")) > 0) {
            this.priceTrend = PriceTrend.UP;
        } else if (changePercent.compareTo(new BigDecimal("-1.0")) < 0) {
            this.priceTrend = PriceTrend.DOWN;
        } else {
            this.priceTrend = PriceTrend.STABLE;
        }
    }
}