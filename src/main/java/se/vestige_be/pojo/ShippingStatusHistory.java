package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import se.vestige_be.pojo.enums.ShippingStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipping_status_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_id", nullable = false)
    @ToString.Exclude
    private ShippingOrder shippingOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShippingStatus status;

    @Column(length = 100)
    private String location;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}