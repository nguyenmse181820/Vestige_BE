package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipping_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shippingId;

    @ManyToOne
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false, length = 30, columnDefinition = "varchar(30)")
    private String status; // pending, processing, picked_up, etc.

    @Column(length = 100, columnDefinition = "varchar(100)")
    private String ghtkTrackingCode;

    @Column(length = 100, columnDefinition = "varchar(100)")
    private String ghtkOrderId;

    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingFee;

    private BigDecimal weight;

    @Column(length = 50, columnDefinition = "varchar(50)")
    private String dimensions;

    @ManyToOne
    @JoinColumn(name = "pickup_address_id", nullable = false)
    private UserAddress pickupAddress;

    @ManyToOne
    @JoinColumn(name = "delivery_address_id", nullable = false)
    private UserAddress deliveryAddress;

    @Column(columnDefinition = "text")
    private String shippingNotes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
