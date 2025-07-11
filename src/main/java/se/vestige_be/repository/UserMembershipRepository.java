package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.UserMembership;
import se.vestige_be.pojo.enums.MembershipStatus;
import java.util.Optional;

public interface UserMembershipRepository extends JpaRepository<UserMembership, Long> {
    Optional<UserMembership> findByUserAndStatus(User user, MembershipStatus status);
    Optional<UserMembership> findByPayosSubscriptionId(String payosSubscriptionId);
    Optional<UserMembership> findFirstByUserAndPlanPlanIdAndStatusOrderByCreatedAtDesc(User user, Long planId, MembershipStatus membershipStatus);
}
