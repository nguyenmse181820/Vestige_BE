package se.vestige_be.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import se.vestige_be.repository.UserRepository;
import se.vestige_be.service.TrustScoreService;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrustScoreUpdateTask {

    private final UserRepository userRepository;
    private final TrustScoreService trustScoreService;

    // Run once every day at 3:00 AM
    @Scheduled(cron = "0 0 3 * * ?")
    public void updateAllUserTrustScores() {
        log.info("Starting nightly Trust Score update task.");
        userRepository.findAll().forEach(trustScoreService::updateUserTrustScore);
        log.info("Finished nightly Trust Score update task.");
    }
}
