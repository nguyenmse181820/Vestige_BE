package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_images")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long imageId;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, columnDefinition = "varchar(255)")
    private String imageUrl;

    private Boolean isPrimary = false;

    @Column(nullable = false)
    private Integer displayOrder;

    @CreationTimestamp
    private LocalDateTime createdAt;
}

