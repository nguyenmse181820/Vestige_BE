package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.Brand;

import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByName(String name);
    boolean existsByName(String name);
}
