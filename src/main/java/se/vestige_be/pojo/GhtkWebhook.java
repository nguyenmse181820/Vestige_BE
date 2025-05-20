package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ghtk_webhooks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GhtkWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long webhookId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_id")
    @ToString.Exclude
    private ShippingOrder shippingOrder;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    @Builder.Default
    private Boolean processed = false;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    public void markAsProcessed() {
        this.processed = true;
        this.processedAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        this.processed = false;
        this.errorMessage = errorMessage;
    }
}