package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.Brand;

import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByName(String name);
    boolean existsByName(String name);
    
    @Query("SELECT COUNT(p) FROM Product p WHERE p.brand.brandId = :brandId")
    long countProductsByBrandId(@Param("brandId") Long brandId);
}
