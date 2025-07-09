package se.vestige_be.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class ProductImageRepositoryImpl implements ProductImageRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean existsByProductIdAndDisplayOrderAndActiveTrue(Long productId, Integer displayOrder, Long excludeImageId) {
        String jpql = "SELECT COUNT(pi) FROM ProductImage pi WHERE pi.product.productId = :productId AND pi.displayOrder = :displayOrder AND pi.active = true" +
                (excludeImageId != null ? " AND pi.imageId <> :excludeImageId" : "");
        var query = entityManager.createQuery(jpql, Long.class)
                .setParameter("productId", productId)
                .setParameter("displayOrder", displayOrder);
        if (excludeImageId != null) {
            query.setParameter("excludeImageId", excludeImageId);
        }
        Long count = query.getSingleResult();
        return count > 0;
    }
}
