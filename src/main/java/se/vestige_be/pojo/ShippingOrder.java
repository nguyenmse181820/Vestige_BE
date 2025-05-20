package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import se.vestige_be.pojo.enums.ShippingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    @ToString.Exclude
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ShippingStatus status = ShippingStatus.PENDING;

    @Column(length = 100)
    private String ghtkTrackingCode;

    @Column(length = 100)
    private String ghtkOrderId;

    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingFee;

    @Column(precision = 10, scale = 2)
    private BigDecimal weight;

    @Column(length = 50)
    private String dimensions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pickup_address_id", nullable = false)
    @ToString.Exclude
    private UserAddress pickupAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_address_id", nullable = false)
    @ToString.Exclude
    private UserAddress deliveryAddress;

    @Column(columnDefinition = "text")
    private String shippingNotes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "shippingOrder", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<ShippingStatusHistory> statusHistory = new ArrayList<>();

    @OneToMany(mappedBy = "shippingOrder", fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<GhtkWebhook> webhooks = new ArrayList<>();
}