package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.UserMembership;
import se.vestige_be.pojo.enums.MembershipStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserMembershipRepository extends JpaRepository<UserMembership, Long> {
    Optional<UserMembership> findByUserAndStatus(User user, MembershipStatus status);
    Optional<UserMembership> findByPayosSubscriptionId(String payosSubscriptionId);
    Optional<UserMembership> findFirstByUserAndPlanPlanIdAndStatusOrderByCreatedAtDesc(User user, Long planId, MembershipStatus membershipStatus);
    
    // Query to find ACTIVE plans whose end_date has passed
    @Query("SELECT m FROM UserMembership m WHERE m.status = 'ACTIVE' AND m.endDate < :date")
    List<UserMembership> findExpiredActivePlans(@Param("date") LocalDateTime date);
    
    // Query to find QUEUED plans whose start_date is today or in the past
    @Query("SELECT m FROM UserMembership m WHERE m.status = 'QUEUED' AND m.startDate <= :date")
    List<UserMembership> findQueuedPlansToActivate(@Param("date") LocalDateTime date);
    
    // Query to find all queued plans for a specific user, ordered by end date descending
    @Query("SELECT m FROM UserMembership m WHERE m.user = :user AND m.status = 'QUEUED' ORDER BY m.endDate DESC")
    List<UserMembership> findQueuedPlansByUser(@Param("user") User user);
}
