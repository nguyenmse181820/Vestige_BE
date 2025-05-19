package se.vestige_be.pojo;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    @ManyToOne
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Column(nullable = false, length = 100, columnDefinition = "varchar(100)")
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(nullable = false, length = 50, columnDefinition = "varchar(50)")
    private String condition;

    @Column(length = 20, columnDefinition = "varchar(20)")
    private String size;

    @Column(length = 50, columnDefinition = "varchar(50)")
    private String color;

    private BigDecimal authenticityConfidenceScore;
    private BigDecimal shippingFee;

    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String status;

    @Column(columnDefinition = "int default 0")
    private Integer viewsCount;

    @Column(columnDefinition = "int default 0")
    private Integer likesCount;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    private LocalDateTime soldAt;
}
