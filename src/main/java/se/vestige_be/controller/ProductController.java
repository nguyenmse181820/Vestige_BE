package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.ProductCreateRequest;
import se.vestige_be.dto.request.ProductImageRequest;
import se.vestige_be.dto.request.ProductUpdateRequest;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.dto.response.PagedResponse;
import se.vestige_be.dto.response.ProductDetailResponse;
import se.vestige_be.dto.response.ProductFilterResponse;
import se.vestige_be.dto.response.ProductListResponse;
import se.vestige_be.pojo.User;
import se.vestige_be.service.ProductService;
import se.vestige_be.service.UserService;
import se.vestige_be.util.PaginationUtils;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Product", description = "API for product management")
public class ProductController {

    private final ProductService productService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProductListResponse>>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long sellerId) {

        ProductFilterResponse filterDto = ProductFilterResponse.builder()
                .search(search)
                .categoryId(categoryId)
                .brandId(brandId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .condition(condition)
                .status(status != null ? status : "ACTIVE")
                .sellerId(sellerId)
                .build();

        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);
        Page<ProductListResponse> products = productService.getProducts(filterDto, pageable);

        Map<String, Object> filters = PaginationUtils.createFilters(search, categoryId, brandId);
        if (minPrice != null || maxPrice != null) {
            filters.put("priceRange", Map.of(
                    "min", minPrice != null ? minPrice : "0",
                    "max", maxPrice != null ? maxPrice : "unlimited"
            ));
        }
        if (condition != null) filters.put("condition", condition);
        if (status != null) filters.put("status", status);
        if (sellerId != null) filters.put("sellerId", sellerId);

        PagedResponse<ProductListResponse> pagedResponse = PagedResponse.of(products, filters);

        return ResponseEntity.ok(ApiResponse.<PagedResponse<ProductListResponse>>builder()
                .message("Products retrieved successfully")
                .data(pagedResponse)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductById(@PathVariable Long id) {
        productService.incrementViewCount(id);

        return productService.getProductById(id)
                .map(product -> ResponseEntity.ok(ApiResponse.<ProductDetailResponse>builder()
                        .status(HttpStatus.OK.toString())
                        .message("Product retrieved successfully")
                        .data(product)
                        .build()))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.<ProductDetailResponse>builder()
                                .status(HttpStatus.NOT_FOUND.toString())
                                .message("Product not found")
                                .build()));
    }

    @GetMapping("/my-products")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PagedResponse<ProductListResponse>>> getMyProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        ProductFilterResponse filterDto = ProductFilterResponse.builder()
                .sellerId(user.getUserId())
                .status(status)
                .build();

        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);
        Page<ProductListResponse> products = productService.getProducts(filterDto, pageable);

        Map<String, Object> filters = Map.of("sellerId", user.getUserId());
        if (status != null) {
            filters = Map.of("sellerId", user.getUserId(), "status", status);
        }

        PagedResponse<ProductListResponse> pagedResponse = PagedResponse.of(products, filters);

        return ResponseEntity.ok(ApiResponse.<PagedResponse<ProductListResponse>>builder()
                .message("Your products retrieved successfully")
                .data(pagedResponse)
                .build());
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(
            @Valid @RequestBody ProductCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            ProductDetailResponse product = productService.createProduct(request, user.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.<ProductDetailResponse>builder()
                            .message("Product created successfully")
                            .data(product)
                            .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<ProductDetailResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            ProductDetailResponse product = productService.updateProduct(id, request, user.getUserId());
            return ResponseEntity.ok(ApiResponse.<ProductDetailResponse>builder()
                    .message("Product updated successfully")
                    .data(product)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<ProductDetailResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        productService.deleteProduct(id, user.getUserId());

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Product deleted successfully")
                .build());
    }

    @PostMapping("/{id}/images")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> addProductImage(
            @PathVariable Long id,
            @Valid @RequestBody ProductImageRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        ProductDetailResponse product = productService.addProductImage(id, request, user.getUserId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ProductDetailResponse>builder()
                        .message("Image added successfully")
                        .data(product)
                        .build());
    }
    @DeleteMapping("/{id}/images/{imageId}")    
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> deleteProductImage(
            @PathVariable Long id,
            @PathVariable Long imageId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        productService.deleteProductImage(id, imageId, user.getUserId());

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Image deleted successfully")
                .build());
    }
}