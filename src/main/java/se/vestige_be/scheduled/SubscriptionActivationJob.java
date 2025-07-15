package se.vestige_be.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.pojo.UserMembership;
import se.vestige_be.pojo.enums.MembershipStatus;
import se.vestige_be.repository.UserMembershipRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionActivationJob {

    private final UserMembershipRepository userMembershipRepository;

    /**
     * Run this job every day at 1 AM to process subscription state transitions
     * This ensures seamless subscription management without user intervention
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void processSubscriptionTransitions() {
        log.info("Starting daily subscription transitions job at {}", LocalDateTime.now());
        
        LocalDateTime now = LocalDateTime.now();
        
        try {
            // 1. Deactivate expired plans
            // Find ACTIVE plans whose end_date has passed
            List<UserMembership> expiredPlans = userMembershipRepository.findExpiredActivePlans(now);
            
            if (!expiredPlans.isEmpty()) {
                log.info("Found {} expired ACTIVE plans to process", expiredPlans.size());
                
                expiredPlans.forEach(plan -> {
                    log.info("Expiring plan for user: {}, plan: {}, ended: {}", 
                            plan.getUser().getUsername(), 
                            plan.getPlan().getName(), 
                            plan.getEndDate());
                    plan.setStatus(MembershipStatus.EXPIRED);
                });
                
                userMembershipRepository.saveAll(expiredPlans);
                log.info("Successfully expired {} plans", expiredPlans.size());
            } else {
                log.info("No expired plans found to process");
            }
            
            // 2. Activate queued plans
            // Find QUEUED plans whose start_date is now or in the past
            List<UserMembership> plansToActivate = userMembershipRepository.findQueuedPlansToActivate(now);
            
            if (!plansToActivate.isEmpty()) {
                log.info("Found {} QUEUED plans ready to activate", plansToActivate.size());
                
                plansToActivate.forEach(plan -> {
                    log.info("Activating queued plan for user: {}, plan: {}, starts: {}", 
                            plan.getUser().getUsername(), 
                            plan.getPlan().getName(), 
                            plan.getStartDate());
                    plan.setStatus(MembershipStatus.ACTIVE);
                });
                
                userMembershipRepository.saveAll(plansToActivate);
                log.info("Successfully activated {} queued plans", plansToActivate.size());
            } else {
                log.info("No queued plans found ready for activation");
            }
            
            log.info("Daily subscription transitions job completed successfully");
            
        } catch (Exception e) {
            log.error("Error during subscription transitions job: {}", e.getMessage(), e);
            throw e; // Re-throw to ensure the scheduler knows about the failure
        }
    }
    
    /**
     * Alternative method to run the job manually for testing purposes
     * This can be called from a controller or test
     */
    public void runSubscriptionTransitionsManually() {
        log.info("Running subscription transitions manually");
        processSubscriptionTransitions();
    }
}
