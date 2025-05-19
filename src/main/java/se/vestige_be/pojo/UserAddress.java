package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_addresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long addressId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100, columnDefinition = "varchar(100)")
    private String addressLine1;

    @Column(length = 100, columnDefinition = "varchar(100)")
    private String addressLine2;

    @Column(nullable = false, length = 50, columnDefinition = "varchar(50)")
    private String city;

    @Column(length = 50, columnDefinition = "varchar(50)")
    private String state;

    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String postalCode;

    @Column(nullable = false, length = 50, columnDefinition = "varchar(50)")
    private String country;

    @Column(nullable = false)
    private Boolean isDefault = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
