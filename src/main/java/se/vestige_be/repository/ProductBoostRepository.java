package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.ProductBoost;
import se.vestige_be.pojo.Product;
import se.vestige_be.pojo.User;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ProductBoostRepository extends JpaRepository<ProductBoost, Long> {
    Optional<ProductBoost> findByProductAndBoostEndTimeAfter(Product product, LocalDateTime currentTime);
    
    @Query("SELECT pb FROM ProductBoost pb WHERE pb.user = :user AND pb.boostEndTime > :currentTime")
    Optional<ProductBoost> findActiveBoostByUser(@Param("user") User user, @Param("currentTime") LocalDateTime currentTime);
}
