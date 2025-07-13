package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import se.vestige_be.pojo.User;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByStripeAccountId(String stripeAccountId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    
    // Additional methods for user statistics and activity monitoring
    long countByJoinedDateAfter(LocalDateTime since);
    long countByLastLoginAtAfter(LocalDateTime since);
    long countByIsVerifiedTrue();
    
    // Methods for admin user summaries
    org.springframework.data.domain.Page<User> findByUsernameContainingOrEmailContaining(String username, String email, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<User> findByAccountStatus(String accountStatus, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<User> findByUsernameContainingOrEmailContainingAndAccountStatus(String username, String email, String accountStatus, org.springframework.data.domain.Pageable pageable);
    
    // Methods for comprehensive statistics
    long countByAccountStatus(String accountStatus);
    long countByJoinedDateBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
    
    // Count distinct sellers who have sold items with the given status
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT oi.seller.userId) FROM OrderItem oi WHERE oi.status = :status")
    long countDistinctSellersByOrderItemStatus(@org.springframework.data.repository.query.Param("status") se.vestige_be.pojo.enums.OrderItemStatus status);
}
