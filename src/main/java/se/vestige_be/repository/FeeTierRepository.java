package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.FeeTier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FeeTierRepository extends JpaRepository<FeeTier, Long> {
    List<FeeTier> findAllByOrderByMinValueAsc();

    // Find tier by value range
    Optional<FeeTier> findByMinValueLessThanEqualAndMaxValueGreaterThanEqual(BigDecimal value1, BigDecimal value2);

    // Find tier by name
    Optional<FeeTier> findByTierName(String tierName);
}