package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ghtk_webhooks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GhtkWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long webhookId;

    @ManyToOne
    @JoinColumn(name = "shipping_id")
    private ShippingOrder shippingOrder;

    @Column(nullable = false, length = 50, columnDefinition = "varchar(50)")
    private String eventType;

    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    private Boolean processed = false;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;
}
