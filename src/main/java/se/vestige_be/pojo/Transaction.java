package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @ManyToOne
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @ManyToOne
    @JoinColumn(name = "seller_id")
    private User seller;

    @ManyToOne
    @JoinColumn(name = "buyer_id")
    private User buyer;

    @ManyToOne
    @JoinColumn(name = "offer_id")
    private Offer offer;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal feePercentage;

    @ManyToOne
    @JoinColumn(name = "shipping_address_id")
    private UserAddress shippingAddress;

    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String status;

    @Column(length = 20, columnDefinition = "varchar(20)")
    private String escrowStatus;

    @Column(length = 100, columnDefinition = "varchar(100)")
    private String trackingNumber;

    @Column(length = 255, columnDefinition = "varchar(255)")
    private String trackingUrl;

    private Boolean buyerProtectionEligible = true;

    @Column(length = 20, columnDefinition = "varchar(20)")
    private String disputeStatus;

    @Column(columnDefinition = "text")
    private String disputeReason;

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
}
