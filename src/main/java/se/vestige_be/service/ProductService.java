package se.vestige_be.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.request.AdminProductUpdateRequest;
import se.vestige_be.dto.request.ProductImageRequest;
import se.vestige_be.dto.request.ProductCreateRequest;
import se.vestige_be.dto.request.ProductUpdateRequest;
import se.vestige_be.dto.response.PagedResponse;
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
import se.vestige_be.repository.ProductImageRepository;
import se.vestige_be.repository.ProductRepository;
import se.vestige_be.repository.UserRepository;
import se.vestige_be.repository.ProductLikeRepository;
import se.vestige_be.repository.ProductBoostRepository;
import se.vestige_be.util.PaginationUtils;
import se.vestige_be.util.SlugUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductLikeRepository productLikeRepository;
    private final ProductBoostRepository productBoostRepository;

    public Page<ProductListResponse> getProducts(ProductFilterResponse filterDto, Pageable pageable) {
        // Check if this is a simple query with default sorting that can benefit from boost prioritization
        if (isSimpleQuery(filterDto) && isDefaultSorting(pageable)) {
            // Use boost prioritization for simple queries
            Page<Product> products = productRepository.findProductsWithBoostPriority(
                    ProductStatus.ACTIVE, LocalDateTime.now(), pageable);
            return products.map(this::convertToListResponse);
        } else {
            // Use specification-based query for complex filtering
            Specification<Product> spec = buildProductSpecification(filterDto);
            Page<Product> products = productRepository.findAll(spec, pageable);
            return products.map(this::convertToListResponse);
        }
    }

    private boolean isSimpleQuery(ProductFilterResponse filterDto) {
        // Consider it simple if no complex filters are applied
        return filterDto.getSearch() == null &&
               filterDto.getCategoryId() == null &&
               filterDto.getBrandId() == null &&
               filterDto.getMinPrice() == null &&
               filterDto.getMaxPrice() == null &&
               filterDto.getCondition() == null &&
               filterDto.getSellerId() == null &&
               (filterDto.getStatus() == null || "ACTIVE".equals(filterDto.getStatus()));
    }

    private boolean isDefaultSorting(Pageable pageable) {
        // Check if sorting is by createdAt desc (default) or no sorting
        if (pageable.getSort().isEmpty()) {
            return true;
        }
        return pageable.getSort().stream()
                .anyMatch(order -> "createdAt".equals(order.getProperty()) && order.isDescending());
    }

    @Transactional(readOnly = true)
    public List<ProductListResponse> getTopViewedProducts(int limit) {
        // Create pageable for top N products by viewsCount
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "viewsCount"));
        Page<Product> page = productRepository.findByStatus(ProductStatus.ACTIVE, pageRequest);
        return page.map(this::convertToListResponse).getContent();
    }

    public PagedResponse<ProductListResponse> getAllProductsWithAnyStatus(
            int page, int size, String sortBy, String sortDir,
            String search, Long categoryId, Long brandId,
            BigDecimal minPrice, BigDecimal maxPrice,
            String condition, String status, Long sellerId) {

        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);

        ProductFilterResponse filterDto = ProductFilterResponse.builder()
                .search(search)
                .categoryId(categoryId)
                .brandId(brandId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .condition(condition)
                .status(status)
                .sellerId(sellerId)
                .build();

        Specification<Product> spec = buildAdminProductSpecification(filterDto);
        Page<Product> products = productRepository.findAll(spec, pageable);
        List<ProductListResponse> responseList = products.getContent().stream()
                .map(this::convertToListResponse)
                .toList();

        PagedResponse.PageMetadata pagination = PagedResponse.PageMetadata.builder()
                .currentPage(products.getNumber())
                .pageSize(products.getSize())
                .totalPages(products.getTotalPages())
                .totalElements(products.getTotalElements())
                .build();

        return PagedResponse.<ProductListResponse>builder()
                .content(responseList)
                .pagination(pagination)
                .build();
    }

    private Specification<Product> buildAdminProductSpecification(ProductFilterResponse filterDto) {
        Specification<Product> spec = Specification.where(null);

        if (filterDto.getSearch() != null && !filterDto.getSearch().trim().isEmpty()) {
            spec = spec.and(hasSearch(filterDto.getSearch()));
        }
        if (filterDto.getCategoryId() != null) {
            spec = spec.and(hasCategoryId(filterDto.getCategoryId()));
        }
        if (filterDto.getBrandId() != null) {
            spec = spec.and(hasBrandId(filterDto.getBrandId()));
        }
        if (filterDto.getMinPrice() != null || filterDto.getMaxPrice() != null) {
            spec = spec.and(hasPriceRange(filterDto.getMinPrice(), filterDto.getMaxPrice()));
        }
        if (filterDto.getCondition() != null && !filterDto.getCondition().trim().isEmpty()) {
            spec = spec.and(hasCondition(filterDto.getCondition()));
        }
        if (filterDto.getSellerId() != null) {
            spec = spec.and(hasSellerId(filterDto.getSellerId()));
        }

        if (filterDto.getStatus() != null && !filterDto.getStatus().trim().isEmpty()) {
            spec = spec.and(hasStatusForAdmin(filterDto.getStatus()));
        }

        return spec;
    }
    private Specification<Product> hasStatusForAdmin(String statusString) {
        return (root, query, criteriaBuilder) -> {
            if (statusString == null || statusString.trim().isEmpty()) {
                return null;
            }
            try {
                ProductStatus status = ProductStatus.valueOf(statusString.toUpperCase());
                return criteriaBuilder.equal(root.get("status"), status);
            } catch (IllegalArgumentException e) {
                return null;
            }
        };
    }    public Optional<ProductDetailResponse> getProductById(Long productId) {
        return productRepository.findByIdWithRelations(productId)
                .map(this::convertToDetailResponse);
    }

    @Transactional
    public ProductDetailResponse createProduct(ProductCreateRequest request, Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with ID: " + sellerId));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        Brand brand = brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with ID: " + request.getBrandId()));        if (request.getOriginalPrice() != null && request.getPrice() != null &&
                request.getOriginalPrice().compareTo(request.getPrice()) < 0) {
            throw new BusinessLogicException("Original price must be greater than or equal to current price.");
        }        // Generate unique slug with meaningful context
        String slug = generateMeaningfulSlug(request.getTitle(), brand.getName(), seller.getUsername(),
                                            request.getCondition(), request.getColor(), request.getSize());
        
        log.debug("Generated slug '{}' for product title '{}' by seller '{}'", slug, request.getTitle(), seller.getUsername());

        Product product = Product.builder()
                .seller(seller)
                .category(category)
                .brand(brand)
                .title(request.getTitle())
                .slug(slug)
                .description(request.getDescription())
                .price(request.getPrice())
                .originalPrice(request.getOriginalPrice())
                .condition(request.getCondition())
                .size(request.getSize())
                .color(request.getColor())
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
                            .active(true)
                            .build())
                    .toList();
            product.getImages().addAll(images);
        }

        Product savedProduct = productRepository.save(product);
        return convertToDetailResponse(savedProduct);
    }    @Transactional
    public ProductDetailResponse updateProduct(Long productId, ProductUpdateRequest request, Long sellerId) {
        Product product = productRepository.findByIdWithRelations(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        // Null safety checks
        if (product.getSeller() == null) {
            log.error("Product {} has null seller", productId);
            throw new BusinessLogicException("Product has no seller assigned");
        }
        
        if (product.getSeller().getUserId() == null) {
            log.error("Product {} seller has null userId", productId);
            throw new BusinessLogicException("Product seller has no userId");
        }
        
        if (sellerId == null) {
            log.error("sellerId is null for product update request");
            throw new BusinessLogicException("User ID is null");
        }
        
        // Security check: Only the seller can update their own product
        if (!product.getSeller().getUserId().equals(sellerId)) {
            throw new UnauthorizedException("You are not authorized to update this product.");
        }

        // Business rule: Cannot update sold products
        if (ProductStatus.SOLD.equals(product.getStatus())) {
            throw new BusinessLogicException("Cannot update a product that has been sold.");
        }        // Enhanced business rule for DRAFT vs non-DRAFT products
        if (ProductStatus.DRAFT.equals(product.getStatus())) {
            // For DRAFT products: Users can update everything freely until admin approval
            // Only restriction: they cannot change status from DRAFT to anything else
            if (request.hasStatus() && !ProductStatus.DRAFT.equals(request.getStatus())) {
                throw new BusinessLogicException("Cannot change status from DRAFT. Only admin can approve draft products.");
            }
            log.debug("Updating DRAFT product {} by seller {} - full edit permissions", productId, sellerId);
        } else {
            // For non-DRAFT products: Restricted updates as before
            // Users can only set ACTIVE or INACTIVE status
            if (request.hasStatus() && !request.isValidUserStatus()) {
                throw new BusinessLogicException("Invalid status. Users can only set ACTIVE or INACTIVE status for approved products.");
            }
            log.debug("Updating approved product {} by seller {} - restricted edit permissions", productId, sellerId);
        }

        if (request.hasCategoryId() && !product.getCategory().getCategoryId().equals(request.getCategoryId())) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));
            product.setCategory(category);
        }

        if (request.hasBrandId() && !product.getBrand().getBrandId().equals(request.getBrandId())) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found with ID: " + request.getBrandId()));
            product.setBrand(brand);
        }

        if (request.hasTitle()) {
            if (!product.getTitle().equals(request.getTitle()) &&
                    productRepository.existsBySellerUserIdAndTitleAndProductIdNot(sellerId, request.getTitle(), productId)) { // Check for other products
                throw new BusinessLogicException("Another product with this title already exists for this seller.");
            }
            product.setTitle(request.getTitle());
        }

        if (request.hasDescription()) product.setDescription(request.getDescription());
        if (request.hasCondition()) product.setCondition(request.getCondition());
        if (request.hasSize()) product.setSize(request.getSize());
        if (request.hasColor()) product.setColor(request.getColor());
        if (request.hasStatus()) product.setStatus(request.getStatus());


        if (request.hasPrice()) {
            BigDecimal originalPriceToCheck = request.hasOriginalPrice() ? request.getOriginalPrice() : product.getOriginalPrice();
            if (originalPriceToCheck != null && originalPriceToCheck.compareTo(request.getPrice()) < 0) {
                throw new BusinessLogicException("Original price must be greater than or equal to current price.");
            }
            product.setPrice(request.getPrice());
        }

        if (request.hasOriginalPrice()) {
            BigDecimal currentPriceToCheck = request.hasPrice() ? request.getPrice() : product.getPrice();
            if (currentPriceToCheck != null && request.getOriginalPrice().compareTo(currentPriceToCheck) < 0) {
                throw new BusinessLogicException("Original price must be greater than or equal to current price.");
            }            product.setOriginalPrice(request.getOriginalPrice());
        }

        if (request.hasImageUrls()) {
            // Delete existing images from database first
            productImageRepository.deleteAll(product.getImages());
            // Then clear the collection
            product.getImages().clear();

            List<ProductImage> newImages = IntStream.range(0, request.getImageUrls().size())
                    .mapToObj(i -> ProductImage.builder()
                            .product(product)
                            .imageUrl(request.getImageUrls().get(i))
                            .isPrimary(i == 0)
                            .displayOrder(i + 1)
                            .active(true)
                            .build())
                    .toList();
            product.getImages().addAll(newImages);
        }

        Product savedProduct = productRepository.save(product);
        return convertToDetailResponse(savedProduct);
    }    @Transactional
    public void deleteProduct(Long productId, Long sellerId) {
        Product product = productRepository.findByIdWithRelations(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        if (!product.getSeller().getUserId().equals(sellerId)) {
            throw new UnauthorizedException("You are not authorized to delete this product.");
        }

        if (ProductStatus.SOLD.equals(product.getStatus())) {
            throw new BusinessLogicException("Cannot delete a product that has been sold.");
        }

        product.setStatus(ProductStatus.DELETED);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
    }    @Transactional
    public ProductDetailResponse manageProductImage(Long productId, ProductImageRequest request, Long sellerId) {
        Product product = productRepository.findByIdWithRelations(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        if (!product.getSeller().getUserId().equals(sellerId)) {
            throw new UnauthorizedException("You are not authorized to modify images for this product.");
        }

        return processImageManagement(product, request);
    }

    // Extract image management logic for reuse
    private ProductDetailResponse processImageManagement(Product product, ProductImageRequest request) {

        if (request.getImageId() != null) {
            ProductImage imageToUpdate = product.getImages().stream()
                    .filter(img -> img.getImageId().equals(request.getImageId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Image not found with ID: " + request.getImageId() + " for this product."));

            boolean modified = false;

            if (request.getActive() != null && !request.getActive().equals(imageToUpdate.getActive())) {
                imageToUpdate.setActive(request.getActive());
                modified = true;
                if (!request.getActive()) {
                    if (Boolean.TRUE.equals(imageToUpdate.getIsPrimary())) {
                        imageToUpdate.setIsPrimary(false);
                        Optional<ProductImage> nextPrimary = product.getImages().stream()
                                .filter(img -> !img.getImageId().equals(imageToUpdate.getImageId()) && Boolean.TRUE.equals(img.getActive()))
                                .min(Comparator.comparingInt(ProductImage::getDisplayOrder));
                        nextPrimary.ifPresent(img -> img.setIsPrimary(true));
                    }
                }
            }

            // Handle primary status change
            if (request.getIsPrimary() != null && !request.getIsPrimary().equals(imageToUpdate.getIsPrimary())) {
                if (request.getIsPrimary()) {
                    if (!Boolean.TRUE.equals(imageToUpdate.getActive())) {
                        throw new BusinessLogicException("Cannot set an inactive image as primary.");
                    }
                    product.getImages().forEach(img -> {
                        if (!img.getImageId().equals(imageToUpdate.getImageId())) {
                            img.setIsPrimary(false);
                        }
                    });
                    imageToUpdate.setIsPrimary(true);
                } else {
                    imageToUpdate.setIsPrimary(false);
                    if (product.getImages().stream().noneMatch(img -> Boolean.TRUE.equals(img.getIsPrimary()) && Boolean.TRUE.equals(img.getActive()) && !img.getImageId().equals(imageToUpdate.getImageId()))) {
                        product.getImages().stream()
                                .filter(img -> Boolean.TRUE.equals(img.getActive()) && !img.getImageId().equals(imageToUpdate.getImageId()))
                                .min(Comparator.comparingInt(ProductImage::getDisplayOrder))
                                .ifPresent(img -> img.setIsPrimary(true));
                    }
                }
                modified = true;
            }
            if (request.getDisplayOrder() != null && !request.getDisplayOrder().equals(imageToUpdate.getDisplayOrder())) {
                imageToUpdate.setDisplayOrder(request.getDisplayOrder());
                modified = true;
            }

            if (request.getImageUrl() != null && !request.getImageUrl().isBlank() && !imageToUpdate.getImageUrl().equals(request.getImageUrl())) {
                imageToUpdate.setImageUrl(request.getImageUrl());
                modified = true;
            }

            if (modified) {
                productImageRepository.save(imageToUpdate);
            }

        } else {
            if (request.getImageUrl() == null || request.getImageUrl().isBlank()) {
                throw new BusinessLogicException("Image URL is required for adding a new image.");
            }
            if (request.getDisplayOrder() == null) {
                throw new BusinessLogicException("Display order is required for adding a new image.");
            }
            checkDuplicateDisplayOrder(product.getProductId(), request.getDisplayOrder(), null);
            // If primary image is being deactivated, activate the next one in order
            if (Boolean.FALSE.equals(request.getIsPrimary())) {
                product.getImages().stream()
                        .filter(img -> Boolean.TRUE.equals(img.getActive()) && !img.getImageId().equals(request.getImageId()))
                        .findFirst()
                        .ifPresent(img -> img.setIsPrimary(true));
            }

            if (Boolean.TRUE.equals(request.getIsPrimary())) {
                product.getImages().forEach(img -> img.setIsPrimary(false));
            }

            ProductImage newImage = ProductImage.builder()
                    .product(product)
                    .imageUrl(request.getImageUrl())
                    .isPrimary(request.getIsPrimary() != null ? request.getIsPrimary() : false)
                    .displayOrder(request.getDisplayOrder())
                    .active(request.getActive() != null ? request.getActive() : true)
                    .build();
            product.getImages().add(newImage);

            if (product.getImages().stream().filter(img -> Boolean.TRUE.equals(img.getActive())).noneMatch(ProductImage::getIsPrimary)) {
                product.getImages().stream()
                        .filter(img -> Boolean.TRUE.equals(img.getActive()))
                        .min(Comparator.comparingInt(ProductImage::getDisplayOrder))
                        .ifPresent(img -> img.setIsPrimary(true));
            }
        }

        Product savedProduct = productRepository.save(product);
        return convertToDetailResponse(savedProduct);
    }


    @Transactional
    public void incrementViewCount(Long productId) {
        productRepository.findById(productId).ifPresent(product -> {
            product.setViewsCount(product.getViewsCount() + 1);
            productRepository.save(product);
        });
    }

    private Specification<Product> buildProductSpecification(ProductFilterResponse filterDto) {
        Specification<Product> spec = Specification.where(hasStatus(filterDto.getStatus() != null ? filterDto.getStatus() : ProductStatus.ACTIVE.name()));

        if (filterDto.getSearch() != null) spec = spec.and(hasSearch(filterDto.getSearch()));
        if (filterDto.getCategoryId() != null) spec = spec.and(hasCategoryId(filterDto.getCategoryId()));
        if (filterDto.getBrandId() != null) spec = spec.and(hasBrandId(filterDto.getBrandId()));
        if (filterDto.getMinPrice() != null || filterDto.getMaxPrice() != null) spec = spec.and(hasPriceRange(filterDto.getMinPrice(), filterDto.getMaxPrice()));
        if (filterDto.getCondition() != null) spec = spec.and(hasCondition(filterDto.getCondition()));
        if (filterDto.getSellerId() != null) spec = spec.and(hasSellerId(filterDto.getSellerId()));

        if (filterDto.getStatus() == null || !ProductStatus.DELETED.name().equalsIgnoreCase(filterDto.getStatus())) {
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("status"), ProductStatus.DELETED));
        }


        return spec;
    }

    private Specification<Product> hasSearch(String search) {
        return (root, query, criteriaBuilder) -> {
            if (search == null || search.trim().isEmpty()) return null;
            String likePattern = "%" + search.toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("brand").get("name")), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("category").get("name")), likePattern)
            );
        };
    }

    private Specification<Product> hasPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();
            if (minPrice != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            return predicate.getExpressions().isEmpty() ? null : predicate;
        };
    }

    private Specification<Product> hasStatus(String statusString) {
        return (root, query, criteriaBuilder) -> {
            if (statusString == null || statusString.trim().isEmpty()) {
                return criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("status"), ProductStatus.ACTIVE),
                        criteriaBuilder.notEqual(root.get("status"), ProductStatus.DELETED)
                );
            }
            try {
                ProductStatus status = ProductStatus.valueOf(statusString.toUpperCase());
                return criteriaBuilder.equal(root.get("status"), status);
            } catch (IllegalArgumentException e) {
                return criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("status"), ProductStatus.ACTIVE),
                        criteriaBuilder.notEqual(root.get("status"), ProductStatus.DELETED)
                );
            }
        };
    }
    private Specification<Product> hasCategoryId(Long categoryId) {
        return (root, query, criteriaBuilder) -> {
            if (categoryId == null) return null;
            
            // Get all category IDs including subcategories for hierarchical filtering
            List<Long> categoryIds = categoryRepository.findCategoryIdWithAllSubcategoryIds(categoryId);
            
            if (categoryIds.isEmpty()) {
                // If no subcategories found, just filter by the original category
                return criteriaBuilder.equal(root.get("category").get("categoryId"), categoryId);
            } else {
                // Filter by any of the category IDs (parent + all subcategories)
                return root.get("category").get("categoryId").in(categoryIds);
            }
        };
    }

    private Specification<Product> hasBrandId(Long brandId) {
        return (root, query, criteriaBuilder) -> brandId == null ? null : criteriaBuilder.equal(root.get("brand").get("brandId"), brandId);
    }

    private Specification<Product> hasCondition(String conditionStr) {
        return (root, query, criteriaBuilder) -> {
            if (conditionStr == null || conditionStr.trim().isEmpty()) return null;
            try {
                ProductCondition condition = ProductCondition.valueOf(conditionStr.toUpperCase());
                return criteriaBuilder.equal(root.get("condition"), condition);
            } catch (IllegalArgumentException e) {
                return null;
            }
        };
    }

    private Specification<Product> hasSellerId(Long sellerId) {
        return (root, query, criteriaBuilder) -> sellerId == null ? null : criteriaBuilder.equal(root.get("seller").get("userId"), sellerId);
    }


    private ProductListResponse convertToListResponse(Product product) {
        String primaryImageUrl = product.getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getActive()) && Boolean.TRUE.equals(img.getIsPrimary()))
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(product.getImages().stream()
                        .filter(img -> Boolean.TRUE.equals(img.getActive()))
                        .min(Comparator.comparingInt(ProductImage::getDisplayOrder))
                        .map(ProductImage::getImageUrl)
                        .orElse(null));

        BigDecimal discountPercentage = null;
        boolean hasDiscount = false;
        if (product.getOriginalPrice() != null && product.getPrice() != null &&
                product.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0 && // Avoid division by zero
                product.getOriginalPrice().compareTo(product.getPrice()) > 0) {
            hasDiscount = true;
            discountPercentage = product.getOriginalPrice().subtract(product.getPrice())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(product.getOriginalPrice(), 2, RoundingMode.HALF_UP);
        }        return ProductListResponse.builder()
                .productId(product.getProductId())
                .title(product.getTitle())
                .slug(product.getSlug())
                .description(product.getDescription())
                .price(product.getPrice())
                .originalPrice(product.getOriginalPrice())
                .condition(product.getCondition() != null ? product.getCondition().name() : null)
                .size(product.getSize())
                .color(product.getColor())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .viewsCount(product.getViewsCount())
                .likesCount(product.getLikesCount())
                .createdAt(product.getCreatedAt())
                .categoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .brandId(product.getBrand() != null ? product.getBrand().getBrandId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .primaryImageUrl(primaryImageUrl)
                .discountPercentage(discountPercentage)
                .hasDiscount(hasDiscount)
                .build();
    }

    private ProductDetailResponse convertToDetailResponse(Product product) {
        BigDecimal discountPercentage = null;
        boolean hasDiscount = false;

        if (product.getOriginalPrice() != null && product.getPrice() != null &&
                product.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0 &&
                product.getOriginalPrice().compareTo(product.getPrice()) > 0) {
            hasDiscount = true;
            discountPercentage = product.getOriginalPrice().subtract(product.getPrice())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(product.getOriginalPrice(), 2, RoundingMode.HALF_UP);
        }

        List<ProductDetailResponse.ProductImageInfo> images = product.getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getActive()))
                .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder))
                .map(img -> ProductDetailResponse.ProductImageInfo.builder()
                        .imageId(img.getImageId())
                        .imageUrl(img.getImageUrl())
                        .isPrimary(img.getIsPrimary())
                        .displayOrder(img.getDisplayOrder())
                        .active(img.getActive())
                        .build())
                .collect(Collectors.toList());
        return ProductDetailResponse.builder()
                .productId(product.getProductId())
                .title(product.getTitle())
                .slug(product.getSlug())
                .description(product.getDescription())
                .price(product.getPrice())
                .originalPrice(product.getOriginalPrice())
                .condition(product.getCondition() != null ? product.getCondition().name() : null)
                .size(product.getSize())
                .color(product.getColor())
                .authenticityConfidenceScore(product.getAuthenticityConfidenceScore())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .viewsCount(product.getViewsCount())
                .likesCount(product.getLikesCount())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .seller(product.getSeller() != null ? ProductDetailResponse.SellerInfo.builder()
                        .userId(product.getSeller().getUserId())
                        .username(product.getSeller().getUsername())
                        .firstName(product.getSeller().getFirstName())
                        .lastName(product.getSeller().getLastName())
                        .profilePictureUrl(product.getSeller().getProfilePictureUrl())
                        .isVerified(product.getSeller().getIsVerified())
                        .sellerRating(product.getSeller().getSellerRating())
                        .sellerReviewsCount(product.getSeller().getSellerReviewsCount())
                        .successfulTransactions(product.getSeller().getSuccessfulTransactions())
                        .joinedDate(product.getSeller().getJoinedDate())
                        .build() : null)
                .category(product.getCategory() != null ? ProductDetailResponse.CategoryInfo.builder()
                        .categoryId(product.getCategory().getCategoryId())
                        .name(product.getCategory().getName())
                        .description(product.getCategory().getDescription())
                        .build() : null)
                .brand(product.getBrand() != null ? ProductDetailResponse.BrandInfo.builder()
                        .brandId(product.getBrand().getBrandId())
                        .name(product.getBrand().getName())
                        .logoUrl(product.getBrand().getLogoUrl())
                        .build() : null)
                .images(images)
                .discountPercentage(discountPercentage)
                .hasDiscount(hasDiscount)                .build();
    }

    /**
     * Admin-only method to update any product with enhanced permissions
     */    @Transactional
    public ProductDetailResponse updateProductAsAdmin(Long productId, AdminProductUpdateRequest request, Long adminId) {
        Product product = productRepository.findByIdWithRelations(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        log.info("Admin {} updating product {} owned by seller {}", 
                adminId, productId, product.getSeller().getUserId());

        // Admin can change seller ownership
        if (request.hasSellerId()) {
            User newSeller = userRepository.findById(request.getSellerId())
                    .orElseThrow(() -> new ResourceNotFoundException("New seller not found with ID: " + request.getSellerId()));
            product.setSeller(newSeller);
            log.info("Admin {} transferred product {} ownership from seller {} to seller {}", 
                    adminId, productId, product.getSeller().getUserId(), request.getSellerId());
        }

        // Update basic fields (similar to user update but without restrictions)
        if (request.hasCategoryId()) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));
            product.setCategory(category);
        }

        if (request.hasBrandId()) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found with ID: " + request.getBrandId()));
            product.setBrand(brand);
        }

        if (request.hasTitle()) {
            // Admin can override title uniqueness check if needed
            product.setTitle(request.getTitle());
        }

        if (request.hasDescription()) product.setDescription(request.getDescription());
        if (request.hasCondition()) product.setCondition(request.getCondition());
        if (request.hasSize()) product.setSize(request.getSize());
        if (request.hasColor()) product.setColor(request.getColor());
        
        // Admin can set any status including SOLD, REPORTED, BANNED, etc.
        if (request.hasStatus()) {
            ProductStatus oldStatus = product.getStatus();
            product.setStatus(request.getStatus());
            
            // Handle special status transitions
            if (request.getStatus() == ProductStatus.SOLD && request.hasForceSoldStatus() && request.getForceSoldStatus()) {
                product.setSoldAt(java.time.LocalDateTime.now());
                log.info("Admin {} forced product {} to SOLD status", adminId, productId);
            } else if (request.getStatus() == ProductStatus.ACTIVE && oldStatus == ProductStatus.SOLD) {
                product.setSoldAt(null);
                log.info("Admin {} reverted product {} from SOLD to ACTIVE", adminId, productId);
            }
        }

        if (request.hasPrice()) {
            product.setPrice(request.getPrice());
        }        if (request.hasOriginalPrice()) {
            product.setOriginalPrice(request.getOriginalPrice());
        }

        if (request.hasImageUrls()) {
            // Delete existing images from database first
            productImageRepository.deleteAll(product.getImages());
            // Then clear the collection
            product.getImages().clear();

            List<ProductImage> newImages = IntStream.range(0, request.getImageUrls().size())
                    .mapToObj(i -> ProductImage.builder()
                            .product(product)
                            .imageUrl(request.getImageUrls().get(i))
                            .isPrimary(i == 0)
                            .displayOrder(i + 1)
                            .active(true)
                            .build())
                    .toList();
            product.getImages().addAll(newImages);
        }

        // Admin-specific fields
        if (request.hasAdminNotes()) {
            // Assuming there's an adminNotes field in Product entity, or log it
            log.info("Admin {} added notes to product {}: {}", adminId, productId, request.getAdminNotes());
            // product.setAdminNotes(request.getAdminNotes()); // Uncomment if field exists
        }

        Product savedProduct = productRepository.save(product);
        log.info("Admin {} successfully updated product {}", adminId, productId);
        
        return convertToDetailResponse(savedProduct);
    }

    /**
     * Get all products for a seller with any status (for seller's own products)
     */
    public PagedResponse<ProductListResponse> getSellerProductsWithAllStatuses(
            Long sellerId, int page, int size, String sortBy, String sortDir, String status) {
        
        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);
        
        ProductFilterResponse filterDto = ProductFilterResponse.builder()
                .sellerId(sellerId)
                .status(status)
                .build();
        
        // Use admin specification to get all statuses for seller's own products
        Specification<Product> spec = buildAdminProductSpecification(filterDto);
        Page<Product> products = productRepository.findAll(spec, pageable);

        List<ProductListResponse> responseList = products.getContent().stream()
                .map(this::convertToListResponse)
                .toList();

        PagedResponse.PageMetadata pagination = PagedResponse.PageMetadata.builder()
                .currentPage(products.getNumber())
                .pageSize(products.getSize())
                .totalPages(products.getTotalPages())
                .totalElements(products.getTotalElements())
                .build();

        return PagedResponse.<ProductListResponse>builder()
                .content(responseList)
                .pagination(pagination)
                .build();
    }
    @Transactional
    public void deleteProductAsAdmin(Long productId, Long adminId) {
        Product product = productRepository.findByIdWithRelations(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        log.info("Admin {} deleting product {} owned by seller {}", 
                adminId, productId, product.getSeller().getUserId());

        // Admin can delete any product regardless of status
        product.setStatus(ProductStatus.DELETED);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("Admin {} successfully deleted product {}", adminId, productId);
    }

    @Transactional
    public ProductDetailResponse manageProductImageAsAdmin(Long productId, ProductImageRequest request, Long adminId) {
        Product product = productRepository.findByIdWithRelations(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));log.info("Admin {} managing images for product {} owned by seller {}", 
                adminId, productId, product.getSeller().getUserId());

        return processImageManagement(product, request);
    }

    private String generateMeaningfulSlug(String title, String brandName, String sellerUsername,
                                        ProductCondition condition, String color, String size) {
        // Ensure we have at least a title to work with
        if (title == null || title.trim().isEmpty()) {
            title = "product";
        }
        if (brandName == null || brandName.trim().isEmpty()) {
            brandName = "brand";
        }
        if (sellerUsername == null || sellerUsername.trim().isEmpty()) {
            sellerUsername = "seller";
        }
        
        String baseSlug = SlugUtils.generateProfessionalProductSlug(
            brandName,
            title, 
            color, 
            size,
            condition != null ? condition.name() : null
        );
        
        log.debug("Generated base slug: '{}' from title: '{}', brand: '{}'", baseSlug, title, brandName);

        if (!productRepository.existsBySlug(baseSlug)) {
            log.debug("Base slug '{}' is available", baseSlug);
            return baseSlug;
        }

        log.debug("Base slug '{}' already exists, checking for multiple sellers", baseSlug);
        boolean hasMultipleSellers = productRepository.hasMultipleSellersForSlug(baseSlug);
        
        if (hasMultipleSellers) {
            String sellerSlug = baseSlug + "-by-" + SlugUtils.generateSlug(sellerUsername);
            log.debug("Multiple sellers detected, trying seller-specific slug: '{}'", sellerSlug);

            if (!productRepository.existsBySlug(sellerSlug)) {
                return sellerSlug;
            }

            int counter = 2;
            String numberedSellerSlug;
            do {
                numberedSellerSlug = sellerSlug + "-" + counter;
                counter++;
            } while (productRepository.existsBySlug(numberedSellerSlug) && counter < 100);
            
            log.debug("Final numbered seller slug: '{}'", numberedSellerSlug);
            return numberedSellerSlug;
        } else {
            int counter = 2;
            String numberedSlug;
            do {
                numberedSlug = baseSlug + "-" + counter;
                counter++;
            } while (productRepository.existsBySlug(numberedSlug) && counter < 100);
            
            log.debug("Final numbered slug: '{}'", numberedSlug);
            return numberedSlug;
        }
    }
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugWithRelations(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with slug: " + slug));
        
        // Increment view count
        product.setViewsCount(product.getViewsCount() + 1);
        productRepository.save(product);
        
        return convertToDetailResponse(product);
    }

    @Transactional(readOnly = true)
    public boolean isSlugAvailable(String slug) {
        return !productRepository.existsBySlug(slug);
    }
    
    /**
     * Preview what slug would be generated for a product
     */
    @Transactional(readOnly = true)
    public String previewProductSlug(String title, String brandName, String sellerUsername,
                                    ProductCondition condition, String color, String size) {
        return generateMeaningfulSlug(title, brandName, sellerUsername, condition, color, size);
    }

    private void checkDuplicateDisplayOrder(Long productId, Integer displayOrder, Long excludeImageId) {
        if (productImageRepository.existsByProductIdAndDisplayOrderAndActiveTrue(productId, displayOrder, excludeImageId)) {
            throw new BusinessLogicException("Display order is already used by another active image for this product.");
        }
    }
    @Transactional
    public void deleteProductImage(Long productId, Long imageId, User user) {
        Product product = productRepository.findByIdWithRelations(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
        boolean isOwner = product.getSeller().getUserId().equals(user.getUserId());
        boolean isAdmin = user.getRole() != null && user.getRole().getName().contains("ADMIN");
        if (!isOwner && !isAdmin) {
            throw new UnauthorizedException("You are not authorized to delete images for this product.");
        }
        ProductImage image = product.getImages().stream()
                .filter(img -> img.getImageId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Image not found with ID: " + imageId + " for this product."));
        product.getImages().remove(image);
        productImageRepository.delete(image);
    }

    @Transactional
    public boolean likeProduct(Long userId, Long productId) {
        if (productLikeRepository.findByUserUserIdAndProductProductId(userId, productId).isPresent()) {
            return false;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        ProductLike like = ProductLike.builder().user(user).product(product).build();
        productLikeRepository.save(like);
        product.setLikesCount(product.getLikesCount() + 1);
        productRepository.save(product);
        return true;
    }

    @Transactional
    public boolean unlikeProduct(Long userId, Long productId) {
        var likeOpt = productLikeRepository.findByUserUserIdAndProductProductId(userId, productId);
        if (likeOpt.isEmpty()) {
            return false;
        }
        productLikeRepository.deleteByUserUserIdAndProductProductId(userId, productId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
        product.setLikesCount(Math.max(0, product.getLikesCount() - 1));
        productRepository.save(product);
        return true;
    }
}