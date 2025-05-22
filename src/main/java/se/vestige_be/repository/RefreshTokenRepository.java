package se.vestige_be.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.RefreshToken;
import se.vestige_be.pojo.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findByUser(User user);
    List<RefreshToken> findByUserAndIsRevokedFalse(User user);
    List<RefreshToken> findByFamilyId(String familyId);
    boolean existsByUserAndIsRevokedFalse(User user);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.isRevoked = true, r.revokedAt = :revokedAt, r.revokedReason = :reason WHERE r.user = :user AND r.isRevoked = false")
    void revokeAllUserTokens(@Param("user") User user, @Param("revokedAt") Instant revokedAt, @Param("reason") String reason);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.isRevoked = true, r.revokedAt = :revokedAt, r.revokedReason = :reason WHERE r.familyId = :familyId")
    void revokeAllFamilyTokens(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt, @Param("reason") String reason);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.expiryDate < :now")
    void deleteAllExpiredTokens(@Param("now") Instant now);
}