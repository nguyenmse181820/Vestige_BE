package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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

    @Column(nullable = false, length = 100, columnDefinition = "varchar(100)")
    private String name;

    @Column(length = 255, columnDefinition = "varchar(255)")
    private String logoUrl;

    private Boolean isVerified = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}