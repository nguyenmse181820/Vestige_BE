package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipping_status_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ShippingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @ManyToOne
    @JoinColumn(name = "shipping_id", nullable = false)
    private ShippingOrder shippingOrder;

    @Column(nullable = false, length = 30, columnDefinition = "varchar(30)")
    private String status;

    @Column(length = 100, columnDefinition = "varchar(100)")
    private String location;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
