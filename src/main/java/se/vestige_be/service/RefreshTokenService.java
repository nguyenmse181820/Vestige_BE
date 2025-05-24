package se.vestige_be.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import se.vestige_be.exception.TokenPossibleCompromiseException;
import se.vestige_be.exception.TokenRefreshException;
import se.vestige_be.pojo.RefreshToken;
import se.vestige_be.pojo.User;
import se.vestige_be.repository.RefreshTokenRepository;
import se.vestige_be.repository.UserRepository;
import se.vestige_be.configuration.JWTTokenUtil;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final JWTTokenUtil jwtTokenUtil;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenDurationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository, CustomUserDetailsService userDetailsService,
            UserRepository userRepository,
            JWTTokenUtil jwtTokenUtil) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    // Create new refresh token on login
    @Transactional
    public RefreshToken createNewRefreshTokenFamily(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));

        // Create new token with new family ID
        RefreshToken refreshToken = RefreshToken.createNewFamily(
                UUID.randomUUID().toString(),
                user,
                Instant.now().plusMillis(refreshTokenDurationMs)
        );

        return refreshTokenRepository.save(refreshToken);
    }

    // Create child token (rotation)
    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken currentToken) {
        if (currentToken.isRevoked()) {
            throw new TokenPossibleCompromiseException(
                    currentToken.getToken(),
                    currentToken.getFamilyId(),
                    "Refresh token reuse detected! Token has already been revoked."
            );
        }

        // Revoke current token
        currentToken.setRevoked(true);
        currentToken.setRevokedAt(Instant.now());
        currentToken.setRevokedReason("Rotation - normal use");
        refreshTokenRepository.save(currentToken);

        // Create new token in the same family
        RefreshToken newToken = currentToken.createChild(
                UUID.randomUUID().toString(),
                Instant.now().plusMillis(refreshTokenDurationMs)
        );

        return refreshTokenRepository.save(newToken);
    }

    // Verify if token is valid
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            token.setRevoked(true);
            token.setRevokedAt(Instant.now());
            token.setRevokedReason("Token expired");
            refreshTokenRepository.save(token);

            throw new TokenRefreshException(token.getToken(), "Refresh token was expired. Please sign in again.");
        }

        if (token.isRevoked()) {
            throw new TokenRefreshException(token.getToken(), "Refresh token was revoked. Please sign in again.");
        }

        return token;
    }

    // Revoke a specific token
    @Transactional
    public void revokeToken(String token, String reason) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new TokenRefreshException(token, "Refresh token not found"));

        refreshToken.setRevoked(true);
        refreshToken.setRevokedAt(Instant.now());
        refreshToken.setRevokedReason(reason);
        refreshTokenRepository.save(refreshToken);
    }

    // Revoke all user tokens
    @Transactional
    public void revokeAllUserTokens(User user, String reason) {
        refreshTokenRepository.revokeAllUserTokens(user, Instant.now(), reason);
    }

    // Revoke all tokens in a family (used when compromise is detected)
    @Transactional
    public void revokeTokenFamily(String familyId, String reason) {
        refreshTokenRepository.revokeAllFamilyTokens(familyId, Instant.now(), reason);
    }

    // Check if token reuse indicates compromise and handle accordingly
    @Transactional
    public void handlePossibleTokenTheft(TokenPossibleCompromiseException ex, String username) {
        // 1. Revoke entire family
        revokeTokenFamily(ex.getFamilyId(), "Security breach - token reuse detected");

        // 2. Optionally, revoke all user tokens for extra security
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
        revokeAllUserTokens(user, "Security breach - token reuse detected");

        // 3. Log security event (implement additional security logging as needed)
        System.out.println("SECURITY ALERT: Token reuse detected for user: " + username +
                ", family: " + ex.getFamilyId());

        // 4. You might want to trigger additional security measures like:
        //    - Forcing password reset
        //    - Notifying the user via email
        //    - Flagging the account for security review
    }

    // Cleanup expired tokens (runs daily at midnight)
    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteAllExpiredTokens(Instant.now());
    }
}