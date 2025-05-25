package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @Column(nullable = false)
    private Instant expiryDate;

    @CreationTimestamp
    private Instant createdAt;

    private Instant revokedAt;

    private String revokedReason;

    @Builder.Default
    private boolean isRevoked = false;

    @Column(nullable = false)
    private String familyId;

    public boolean isValid() {
        return !isRevoked && expiryDate.isAfter(Instant.now());
    }

    // generate a token in the same family
    public RefreshToken createChild(String newTokenValue, Instant newExpiryDate) {
        return RefreshToken.builder()
                .token(newTokenValue)
                .user(this.user)
                .expiryDate(newExpiryDate)
                .familyId(this.familyId)
                .build();
    }

    // generate a completely new token family
    public static RefreshToken createNewFamily(String tokenValue, User user, Instant expiryDate) {
        return RefreshToken.builder()
                .token(tokenValue)
                .user(user)
                .expiryDate(expiryDate)
                .familyId(UUID.randomUUID().toString())
                .build();
    }
}