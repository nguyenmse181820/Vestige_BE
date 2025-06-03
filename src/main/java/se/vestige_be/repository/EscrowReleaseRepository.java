package se.vestige_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import se.vestige_be.pojo.EscrowRelease;

import java.time.LocalDateTime;
import java.util.List;

public interface EscrowReleaseRepository extends JpaRepository<EscrowRelease, Long> {

    // Find releases by status
    List<EscrowRelease> findByStatusOrderByCreatedAtDesc(String status);

    // Find releases by date range
    List<EscrowRelease> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);
}