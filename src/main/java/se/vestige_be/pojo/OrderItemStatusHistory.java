package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import se.vestige_be.pojo.enums.OrderItemStatus;

@Entity
@Table(name = "order_item_status_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderItemStatus status;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    // Optional but recommended: who made the change?
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "notes", length = 512)
    private String notes;
}
