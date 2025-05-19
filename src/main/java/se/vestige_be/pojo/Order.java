package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @ManyToOne
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne
    @JoinColumn(name = "shipping_address_id")
    private UserAddress shippingAddress;

    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String status; // pending, paid, processing, shipped, delivered, cancelled, refunded

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;
}
