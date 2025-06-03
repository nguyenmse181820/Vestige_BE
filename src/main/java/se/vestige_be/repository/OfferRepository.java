package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.Offer;

public interface OfferRepository extends JpaRepository<Offer, Long> {
}