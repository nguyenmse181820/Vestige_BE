package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.UserRole;

public interface UserRoleRepository extends JpaRepository<UserRole,Integer> {
}
