package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import se.vestige_be.pojo.enums.DisputeStatus;
import se.vestige_be.pojo.enums.EscrowStatus;
import se.vestige_be.pojo.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    @ToString.Exclude
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    @ToString.Exclude
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    @ToString.Exclude
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id")
    @ToString.Exclude
    private Offer offer;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal feePercentage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_address_id")
    @ToString.Exclude
    private UserAddress shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private EscrowStatus escrowStatus = EscrowStatus.HOLDING;

    @Column(length = 100)
    private String trackingNumber;

    @Column(length = 255)
    private String trackingUrl;

    @Builder.Default
    private Boolean buyerProtectionEligible = true;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DisputeStatus disputeStatus;

    @Column(columnDefinition = "text")
    private String disputeReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<ShippingOrder> shippingOrders = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<Review> reviews = new ArrayList<>();
}