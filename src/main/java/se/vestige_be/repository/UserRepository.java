package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import se.vestige_be.pojo.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByStripeAccountId(String stripeAccountId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
