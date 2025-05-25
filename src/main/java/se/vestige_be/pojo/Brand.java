package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "brands")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long brandId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String logoUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "brand", fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<Product> products = new ArrayList<>();

    @OneToMany(mappedBy = "brand", fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<MarketData> marketData = new ArrayList<>();
}