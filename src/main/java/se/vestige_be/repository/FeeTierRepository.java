package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.FeeTier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FeeTierRepository extends JpaRepository<FeeTier, Long> {
    Optional<FeeTier> findByName(String name);
}