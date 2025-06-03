package se.vestige_be.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.request.ProductImageRequest;
import se.vestige_be.dto.request.ProductCreateRequest;
import se.vestige_be.dto.request.ProductUpdateRequest;
import se.vestige_be.dto.response.ProductDetailResponse;
import se.vestige_be.dto.response.ProductFilterResponse;
import se.vestige_be.dto.response.ProductListResponse;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.exception.ResourceNotFoundException;
import se.vestige_be.exception.UnauthorizedException;
import se.vestige_be.pojo.*;
import se.vestige_be.pojo.enums.ProductCondition;
import se.vestige_be.pojo.enums.ProductStatus;
import se.vestige_be.repository.BrandRepository;
import se.vestige_be.repository.CategoryRepository;
import se.vestige_be.repository.ProductRepository;
import se.vestige_be.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public Page<ProductListResponse> getProducts(ProductFilterResponse filterDto, Pageable pageable) {
        Specification<Product> spec = buildProductSpecification(filterDto);
        Page<Product> products = productRepository.findAll(spec, pageable);
        return products.map(this::convertToListResponse);
    }

    public Optional<ProductDetailResponse> getProductById(Long productId) {
        return productRepository.findById(productId)
                .map(this::convertToDetailResponse);
    }

    @Transactional
    public ProductDetailResponse createProduct(ProductCreateRequest request, Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not found"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Brand brand = brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new RuntimeException("Brand not found"));

        if (productRepository.existsBySellerUserIdAndTitle(sellerId, request.getTitle())) {
            throw new RuntimeException("Product with this title already exists for this seller");
        }

        if (request.getOriginalPrice() != null &&
                request.getOriginalPrice().compareTo(request.getPrice()) < 0) {
            throw new RuntimeException("Original price must be greater than or equal to current price");
        }

        Product product = Product.builder()
                .seller(seller)
                .category(category)
                .brand(brand)
                .title(request.getTitle())
                .description(request.getDescription())
                .price(request.getPrice())
                .originalPrice(request.getOriginalPrice())
                .condition(request.getCondition())
                .size(request.getSize())
                .color(request.getColor())
                .shippingFee(request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO)
                .status(request.getStatus() != null ? request.getStatus() : ProductStatus.DRAFT)
                .viewsCount(0)
                .likesCount(0)
                .build();

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            List<ProductImage> images = IntStream.range(0, request.getImageUrls().size())
                    .mapToObj(i -> ProductImage.builder()
                            .product(product)
                            .imageUrl(request.getImageUrls().get(i))
                            .isPrimary(i == 0)
                            .displayOrder(i + 1)
                            .build())
                    .toList();
            product.getImages().addAll(images);
        }

        Product savedProduct = productRepository.save(product);
        return convertToDetailResponse(savedProduct);
    }

    @Transactional
    public ProductDetailResponse updateProduct(Long productId, ProductUpdateRequest request, Long sellerId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (!product.getSeller().getUserId().equals(sellerId)) {
            throw new UnauthorizedException("You can only update your own products");
        }

        if (ProductStatus.SOLD.equals(product.getStatus())) {
            throw new UnauthorizedException("Cannot update sold products");
        }

        if (request.hasCategoryId()) {
            if (!product.getCategory().getCategoryId().equals(request.getCategoryId())) {
                Category category = categoryRepository.findById(request.getCategoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
                product.setCategory(category);
            }
        }

        if (request.hasBrandId()) {
            if (!product.getBrand().getBrandId().equals(request.getBrandId())) {
                Brand brand = brandRepository.findById(request.getBrandId())
                        .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
                product.setBrand(brand);
            }
        }

        if (request.hasTitle()) {
            if (!product.getTitle().equals(request.getTitle()) &&
                    productRepository.existsBySellerUserIdAndTitle(sellerId, request.getTitle())) {
                throw new RuntimeException("Product with this title already exists for this seller");
            }
            product.setTitle(request.getTitle());
        }

        if (request.hasDescription()) {
            product.setDescription(request.getDescription());
        }

        if (request.hasPrice()) {
            BigDecimal originalPriceToCheck = request.hasOriginalPrice() ?
                    request.getOriginalPrice() : product.getOriginalPrice();

            if (originalPriceToCheck != null &&
                    originalPriceToCheck.compareTo(request.getPrice()) < 0) {
                throw new BusinessLogicException("Original price must be greater than or equal to current price");
            }
            product.setPrice(request.getPrice());
        }

        if (request.hasOriginalPrice()) {
            BigDecimal currentPriceToCheck = request.hasPrice() ?
                    request.getPrice() : product.getPrice();

            if (request.getOriginalPrice().compareTo(currentPriceToCheck) < 0) {
                throw new BusinessLogicException("Original price must be greater than or equal to current price");
            }
            product.setOriginalPrice(request.getOriginalPrice());
        }

        if (request.hasCondition()) {
            product.setCondition(request.getCondition());
        }

        if (request.hasSize()) {
            product.setSize(request.getSize());
        }

        if (request.hasColor()) {
            product.setColor(request.getColor());
        }

        if (request.hasShippingFee()) {
            product.setShippingFee(request.getShippingFee());
        }

        if (request.hasStatus()) {
            product.setStatus(request.getStatus());
        }

        if (request.hasImageUrls()) {
            product.getImages().clear();

            List<ProductImage> newImages = IntStream.range(0, request.getImageUrls().size())
                    .mapToObj(i -> ProductImage.builder()
                            .product(product)
                            .imageUrl(request.getImageUrls().get(i))
                            .isPrimary(i == 0)
                            .displayOrder(i + 1)
                            .build())
                    .toList();

            product.getImages().addAll(newImages);
        }

        Product savedProduct = productRepository.save(product);
        return convertToDetailResponse(savedProduct);
    }

    @Transactional
    public void deleteProduct(Long productId, Long sellerId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getSeller().getUserId().equals(sellerId)) {
            throw new RuntimeException("You can only delete your own products");
        }

        if (ProductStatus.SOLD.equals(product.getStatus())) {
            throw new RuntimeException("Cannot delete sold products");
        }

        product.setStatus(ProductStatus.DELETED);
        productRepository.save(product);
    }

    @Transactional
    public ProductDetailResponse addProductImage(Long productId, ProductImageRequest request, Long sellerId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getSeller().getUserId().equals(sellerId)) {
            throw new RuntimeException("You can only modify your own products");
        }

        if (request.getIsPrimary()) {
            product.getImages().forEach(img -> img.setIsPrimary(false));
        }

        int targetDisplayOrder = request.getDisplayOrder();

        product.getImages().stream()
                .filter(img -> img.getDisplayOrder() >= targetDisplayOrder)
                .forEach(img -> img.setDisplayOrder(img.getDisplayOrder() + 1));

        ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(request.getImageUrl())
                .isPrimary(request.getIsPrimary())
                .displayOrder(targetDisplayOrder)
                .build();

        product.getImages().add(image);
        Product savedProduct = productRepository.save(product);
        return convertToDetailResponse(savedProduct);
    }

    @Transactional
    public void deleteProductImage(Long productId, Long imageId, Long sellerId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (!product.getSeller().getUserId().equals(sellerId)) {
            throw new BusinessLogicException("You can only modify your own products");
        }

        product.getImages().removeIf(img -> img.getImageId().equals(imageId));
        productRepository.save(product);
    }

    @Transactional
    public void incrementViewCount(Long productId) {
        productRepository.findById(productId).ifPresent(product -> {
            product.setViewsCount(product.getViewsCount() + 1);
            productRepository.save(product);
        });
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
                return null;
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
        String primaryImageUrl = null;
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            primaryImageUrl = product.getImages().stream()
                    .filter(ProductImage::getIsPrimary)
                    .map(ProductImage::getImageUrl)
                    .findFirst()
                    .orElse(product.getImages().getFirst().getImageUrl());
        }

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

    private ProductDetailResponse convertToDetailResponse(Product product) {
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

        List<ProductDetailResponse.ProductImageInfo> images = product.getImages().stream()
                .map(img -> ProductDetailResponse.ProductImageInfo.builder()
                        .imageId(img.getImageId())
                        .imageUrl(img.getImageUrl())
                        .isPrimary(img.getIsPrimary())
                        .displayOrder(img.getDisplayOrder())
                        .build())
                .toList();

        return ProductDetailResponse.builder()
                .productId(product.getProductId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .originalPrice(product.getOriginalPrice())
                .condition(product.getCondition().name())
                .size(product.getSize())
                .color(product.getColor())
                .authenticityConfidenceScore(product.getAuthenticityConfidenceScore())
                .shippingFee(product.getShippingFee())
                .status(product.getStatus().name())
                .viewsCount(product.getViewsCount())
                .likesCount(product.getLikesCount())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .seller(ProductDetailResponse.SellerInfo.builder()
                        .userId(product.getSeller().getUserId())
                        .username(product.getSeller().getUsername())
                        .firstName(product.getSeller().getFirstName())
                        .lastName(product.getSeller().getLastName())
                        .profilePictureUrl(product.getSeller().getProfilePictureUrl())
                        .isLegitProfile(product.getSeller().getIsLegitProfile())
                        .sellerRating(product.getSeller().getSellerRating())
                        .sellerReviewsCount(product.getSeller().getSellerReviewsCount())
                        .successfulTransactions(product.getSeller().getSuccessfulTransactions())
                        .joinedDate(product.getSeller().getJoinedDate())
                        .build())
                .category(ProductDetailResponse.CategoryInfo.builder()
                        .categoryId(product.getCategory().getCategoryId())
                        .name(product.getCategory().getName())
                        .description(product.getCategory().getDescription())
                        .build())
                .brand(ProductDetailResponse.BrandInfo.builder()
                        .brandId(product.getBrand().getBrandId())
                        .name(product.getBrand().getName())
                        .logoUrl(product.getBrand().getLogoUrl())
                        .build())
                .images(images)
                .discountPercentage(discountPercentage)
                .hasDiscount(hasDiscount)
                .build();
    }
}