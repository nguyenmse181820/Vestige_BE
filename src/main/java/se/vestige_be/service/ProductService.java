package se.vestige_be.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.response.ProductFilterResponse;
import se.vestige_be.dto.response.ProductListResponse;
import se.vestige_be.pojo.Product;
import se.vestige_be.pojo.ProductImage;
import se.vestige_be.pojo.enums.ProductCondition;
import se.vestige_be.pojo.enums.ProductStatus;
import se.vestige_be.repository.ProductRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public Page<ProductListResponse> getProducts(ProductFilterResponse filterDto, Pageable pageable) {
        Specification<Product> spec = buildProductSpecification(filterDto);
        Page<Product> products = productRepository.findAll(spec, pageable);
        return products.map(this::convertToListResponse);
    }

    private Specification<Product> buildProductSpecification(ProductFilterResponse filterDto) {
        return Specification.where(hasStatus(filterDto.getStatus()))
                .and(hasSearch(filterDto.getSearch()))
                .and(hasCategoryId(filterDto.getCategoryId()))
                .and(hasBrandId(filterDto.getBrandId()))
                .and(hasPriceRange(filterDto.getMinPrice(), filterDto.getMaxPrice()))
                .and(hasCondition(filterDto.getCondition()))
                .and(hasSellerId(filterDto.getSellerId()));
    }

    private Specification<Product> hasSearch(String search) {
        return (root, query, criteriaBuilder) -> {
            if (search == null || search.trim().isEmpty()) {
                return null;
            }
            String likePattern = "%" + search.toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("brand").get("name")), likePattern)
            );
        };
    }

    private Specification<Product> hasPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();
            if (minPrice != null) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            return predicate.getExpressions().isEmpty() ? null : predicate;
        };
    }

    private Specification<Product> hasStatus(String status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null || status.isEmpty()) {
                return criteriaBuilder.equal(root.get("status"), ProductStatus.ACTIVE);
            }
            try {
                ProductStatus productStatus = ProductStatus.valueOf(status.toUpperCase());
                return criteriaBuilder.equal(root.get("status"), productStatus);
            } catch (IllegalArgumentException e) {
                return criteriaBuilder.equal(root.get("status"), ProductStatus.ACTIVE);
            }
        };
    }

    private Specification<Product> hasCategoryId(Long categoryId) {
        return (root, query, criteriaBuilder) -> {
            if (categoryId == null) {
                return null;
            }
            return criteriaBuilder.equal(root.get("category").get("categoryId"), categoryId);
        };
    }

    private Specification<Product> hasBrandId(Long brandId) {
        return (root, query, criteriaBuilder) -> {
            if (brandId == null) {
                return null;
            }
            return criteriaBuilder.equal(root.get("brand").get("brandId"), brandId);
        };
    }

    private Specification<Product> hasCondition(String condition) {
        return (root, query, criteriaBuilder) -> {
            if (condition == null || condition.isEmpty()) {
                return null;
            }
            try {
                ProductCondition productCondition = ProductCondition.valueOf(condition.toUpperCase());
                return criteriaBuilder.equal(root.get("condition"), productCondition);
            } catch (IllegalArgumentException e) {
                return null; // Invalid condition, ignore filter
            }
        };
    }

    private Specification<Product> hasSellerId(Long sellerId) {
        return (root, query, criteriaBuilder) -> {
            if (sellerId == null) {
                return null;
            }
            return criteriaBuilder.equal(root.get("seller").get("userId"), sellerId);
        };
    }

    private ProductListResponse convertToListResponse(Product product) {
        // Get primary image
        String primaryImageUrl = null;
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            primaryImageUrl = product.getImages().stream()
                    .filter(ProductImage::getIsPrimary)
                    .map(ProductImage::getImageUrl)
                    .findFirst()
                    .orElse(product.getImages().getFirst().getImageUrl());
        }

        // Calculate discount
        BigDecimal discountPercentage = null;
        boolean hasDiscount = false;

        if (product.getOriginalPrice() != null &&
                product.getOriginalPrice().compareTo(product.getPrice()) > 0) {
            hasDiscount = true;
            discountPercentage = product.getOriginalPrice()
                    .subtract(product.getPrice())
                    .divide(product.getOriginalPrice(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return ProductListResponse.builder()
                .productId(product.getProductId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .originalPrice(product.getOriginalPrice())
                .condition(product.getCondition().name())
                .size(product.getSize())
                .color(product.getColor())
                .shippingFee(product.getShippingFee())
                .status(product.getStatus().name())
                .viewsCount(product.getViewsCount())
                .likesCount(product.getLikesCount())
                .createdAt(product.getCreatedAt())
                .sellerId(product.getSeller().getUserId())
                .sellerUsername(product.getSeller().getUsername())
                .sellerIsLegitProfile(product.getSeller().getIsLegitProfile())
                .sellerRating(product.getSeller().getSellerRating())
                .categoryId(product.getCategory().getCategoryId())
                .categoryName(product.getCategory().getName())
                .brandId(product.getBrand().getBrandId())
                .brandName(product.getBrand().getName())
                .primaryImageUrl(primaryImageUrl)
                .discountPercentage(discountPercentage)
                .hasDiscount(hasDiscount)
                .build();
    }
}