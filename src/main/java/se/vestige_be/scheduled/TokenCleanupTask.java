package se.vestige_be.scheduled;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import se.vestige_be.repository.RefreshTokenRepository;

import java.time.Instant;

@Component
public class TokenCleanupTask {

    private final RefreshTokenRepository refreshTokenRepository;

    public TokenCleanupTask(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // Run once a day at midnight
    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        refreshTokenRepository.deleteAllExpiredTokens(now);
    }
}