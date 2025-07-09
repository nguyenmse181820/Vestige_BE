package se.vestige_be.repository;

public interface ProductImageRepositoryCustom {
    boolean existsByProductIdAndDisplayOrderAndActiveTrue(Long productId, Integer displayOrder, Long excludeImageId);
}
