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

    @Scheduled(cron = "0 0 0 * * ?")
    public void updateAllUserTrustScores() {
        userRepository.findAll().forEach(trustScoreService::updateUserTrustScore);
    }
}
