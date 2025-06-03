package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.UserAddress;

import java.util.List;
import java.util.Optional;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByUserUserId(Long userId);
    List<UserAddress> findByUserUserIdAndIsDefaultTrue(Long userId);
    List<UserAddress> findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);
}