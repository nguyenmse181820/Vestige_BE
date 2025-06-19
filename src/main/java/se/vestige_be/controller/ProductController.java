package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import se.vestige_be.dto.request.AdminProductUpdateRequest;
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
@Tag(name = "Product Management", 
     description = "Complete product management API with role-based access control. " +
                   "Supports public browsing, seller product management, and admin operations. " +
                   "Endpoints are clearly separated between user and admin capabilities.")
public class ProductController {

    private final ProductService productService;
    private final UserService userService;
    @Operation(
            summary = "Get all active products with filtering and pagination",
            description = "Retrieve a paginated list of active products. Supports filtering by search term, category, brand, price range, condition, and seller. This endpoint is public and returns only ACTIVE products by default."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Products retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters"
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProductListResponse>>> getProducts(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Number of items per page", example = "12")
            @RequestParam(defaultValue = "12") int size,
            
            @Parameter(description = "Field to sort by", example = "createdAt", 
                      schema = @Schema(allowableValues = {"createdAt", "title", "price", "viewCount"}))
            @RequestParam(defaultValue = "createdAt") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc",
                      schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(defaultValue = "desc") String sortDir,
            
            @Parameter(description = "Search term to filter by title, description, brand or category")
            @RequestParam(required = false) String search,
            
            @Parameter(description = "Filter by category ID")
            @RequestParam(required = false) Long categoryId,
            
            @Parameter(description = "Filter by brand ID")
            @RequestParam(required = false) Long brandId,
            
            @Parameter(description = "Minimum price filter", example = "100000")
            @RequestParam(required = false) BigDecimal minPrice,
            
            @Parameter(description = "Maximum price filter", example = "500000")
            @RequestParam(required = false) BigDecimal maxPrice,
            
            @Parameter(description = "Filter by product condition",
                      schema = @Schema(allowableValues = {"NEW", "LIKE_NEW", "GOOD", "FAIR", "POOR"}))
            @RequestParam(required = false) String condition,
            
            @Parameter(description = "Filter by product status (usually ACTIVE for public)", 
                      schema = @Schema(allowableValues = {"ACTIVE", "INACTIVE", "SOLD", "DELETED"}))
            @RequestParam(required = false) String status,
            
            @Parameter(description = "Filter by seller ID")
            @RequestParam(required = false) Long sellerId) {

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
                .status(HttpStatus.OK.toString())
                .message("Products retrieved successfully")
                .data(pagedResponse)
                .build());    }

    @Operation(
            summary = "Get product details by ID",
            description = "Retrieve detailed information about a specific product. This endpoint is public and also increments the product's view count."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Product retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Product not found"
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductById(
            @Parameter(description = "Product ID", required = true, example = "1")
            @PathVariable Long id) {
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
                                .message("Product not found with ID: " + id)
                                .build()));
    }

    @Operation(
            summary = "[ADMIN] Get all products with any status",
            description = "Admin-only endpoint to retrieve all products regardless of status (including INACTIVE, SOLD, DELETED, etc.). Supports all filtering options."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "All products retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/all-statuses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<ProductListResponse>>> getAllProductsWithAnyStatus(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Field to sort by", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDir,
            
            @Parameter(description = "Search term to filter by title, description, brand or category")
            @RequestParam(required = false) String search,
            
            @Parameter(description = "Filter by category ID")
            @RequestParam(required = false) Long categoryId,
            
            @Parameter(description = "Filter by brand ID")
            @RequestParam(required = false) Long brandId,
            
            @Parameter(description = "Minimum price filter")
            @RequestParam(required = false) BigDecimal minPrice,
            
            @Parameter(description = "Maximum price filter")
            @RequestParam(required = false) BigDecimal maxPrice,
              @Parameter(description = "Filter by product condition")
            @RequestParam(required = false) String condition,
            
            @Parameter(description = "Filter by product status (ALL statuses allowed for admin)", 
                      schema = @Schema(allowableValues = {"ACTIVE", "INACTIVE", "SOLD", "DELETED", "REPORTED", "BANNED"}))
            @RequestParam(required = false) String status,
            
            @Parameter(description = "Filter by seller ID")
            @RequestParam(required = false) Long sellerId) {

        PagedResponse<ProductListResponse> pagedResponse = productService.getAllProductsWithAnyStatus(
                page, size, sortBy, sortDir,
                search, categoryId, brandId, minPrice, maxPrice,
                condition, status, sellerId
        );

        return ResponseEntity.ok(ApiResponse.<PagedResponse<ProductListResponse>>builder()
                .status(HttpStatus.OK.toString())
                .message("All products with any status retrieved successfully for admin.")
                .data(pagedResponse)                .build());
    }

    @Operation(
            summary = "Get seller's own products with all statuses",
            description = "Allows authenticated sellers to retrieve all their own products regardless of status (ACTIVE, INACTIVE, SOLD, etc.). This is different from the public endpoint which only shows ACTIVE products."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Seller's products retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - User role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/my-products")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PagedResponse<ProductListResponse>>> getMyProducts(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Number of items per page", example = "12")
            @RequestParam(defaultValue = "12") int size,
            
            @Parameter(description = "Field to sort by", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDir,
            
            @Parameter(description = "Filter by product status (all statuses allowed for own products)",
                      schema = @Schema(allowableValues = {"ACTIVE", "INACTIVE", "SOLD", "DELETED"}))
            @RequestParam(required = false) String status,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        // Use the new method that allows sellers to get all their products with any status
        PagedResponse<ProductListResponse> pagedResponse = productService.getSellerProductsWithAllStatuses(
                user.getUserId(), page, size, sortBy, sortDir, status);

        return ResponseEntity.ok(ApiResponse.<PagedResponse<ProductListResponse>>builder()
                .status(HttpStatus.OK.toString())
                .message("Your products retrieved successfully")
                .data(pagedResponse)
                .build());    }

    @Operation(
            summary = "Create a new product",
            description = "Allows authenticated users to create a new product listing. The product will be associated with the authenticated user as the seller."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Product created successfully",
                    content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid product data"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - User role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(
            @Parameter(description = "Product creation data", required = true)
            @Valid @RequestBody ProductCreateRequest request,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername());
        ProductDetailResponse product = productService.createProduct(request, user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ProductDetailResponse>builder()
                        .status(HttpStatus.CREATED.toString())
                        .message("Product created successfully")
                        .data(product)                        .build());
    }

    @Operation(
            summary = "Update seller's own product",
            description = "Allows authenticated sellers to update their own products. Users can only set ACTIVE or INACTIVE status and cannot update SOLD products."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Product updated successfully",
                    content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid product data or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Not the product owner or invalid role"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Product not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @Parameter(description = "Product ID", required = true, example = "1")
            @PathVariable Long id,
            
            @Parameter(description = "Product update data (restricted to user capabilities)", required = true)
            @Valid @RequestBody ProductUpdateRequest request,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername());
        ProductDetailResponse product = productService.updateProduct(id, request, user.getUserId());
        return ResponseEntity.ok(ApiResponse.<ProductDetailResponse>builder()
                .status(HttpStatus.OK.toString())
                .message("Product updated successfully")
                .data(product)
                .build());    }

    @Operation(
            summary = "[ADMIN] Update any product with unrestricted access",
            description = "Admin-only endpoint to update any product with full privileges. Admins can set any status, transfer ownership, and override business rules."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Product updated successfully by admin",
                    content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid product data"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Product not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProductAsAdmin(
            @Parameter(description = "Product ID", required = true, example = "1")
            @PathVariable Long id,
            
            @Parameter(description = "Admin product update data with unrestricted capabilities", required = true)
            @Valid @RequestBody AdminProductUpdateRequest request,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {
        User admin = userService.findByUsername(userDetails.getUsername());
        ProductDetailResponse product = productService.updateProductAsAdmin(id, request, admin.getUserId());
        return ResponseEntity.ok(ApiResponse.<ProductDetailResponse>builder()
                .status(HttpStatus.OK.toString())
                .message("Product updated successfully by admin")
                .data(product)                .build());
    }

    @Operation(
            summary = "Delete seller's own product",
            description = "Allows authenticated sellers to soft-delete their own products (sets status to DELETED). Cannot delete SOLD products."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Product marked as deleted successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Cannot delete SOLD products or other business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Not the product owner"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Product not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @Parameter(description = "Product ID", required = true, example = "1")
            @PathVariable Long id,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername());
        productService.deleteProduct(id, user.getUserId());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.toString())
                .message("Product marked as deleted successfully")
                .build());    }

    @Operation(
            summary = "[ADMIN] Delete any product",
            description = "Admin-only endpoint to delete any product regardless of status or owner. This is a soft delete that sets the status to DELETED."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Product deleted successfully by admin"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Product not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProductAsAdmin(
            @Parameter(description = "Product ID", required = true, example = "1")
            @PathVariable Long id,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {
        User admin = userService.findByUsername(userDetails.getUsername());
        productService.deleteProductAsAdmin(id, admin.getUserId());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.toString())
                .message("Product deleted successfully by admin")
                .build());    }

    @Operation(
            summary = "Manage product images for seller's own product",
            description = "Allows authenticated sellers to add, update, or soft-delete images on their own products. Can set primary images, display order, and manage active status."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Image updated successfully",
                    content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Image added successfully",
                    content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid image data or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Not the product owner"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Product or image not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}/images")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> manageProductImage(
            @Parameter(description = "Product ID", required = true, example = "1")
            @PathVariable("id") Long productId,
            
            @Parameter(description = "Image management data (add new image if imageId is null, update existing if imageId is provided)", required = true)
            @Valid @RequestBody ProductImageRequest request,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        ProductDetailResponse product = productService.manageProductImage(productId, request, user.getUserId());

        String message;
        HttpStatus status;

        if (request.getImageId() != null) {
            if (request.getActive() != null && !request.getActive()) {
                message = "Image soft-deleted successfully.";
            } else if (request.getActive() != null){
                message = "Image reactivated/updated successfully.";
            }
            else {
                message = "Image updated successfully.";
            }
            status = HttpStatus.OK;
        } else {
            message = "Image added successfully.";
            status = HttpStatus.CREATED;
        }

        return ResponseEntity.status(status)
                .body(ApiResponse.<ProductDetailResponse>builder()
                        .status(status.toString())
                        .message(message)
                        .data(product)
                        .build());    }

    @Operation(
            summary = "[ADMIN] Manage product images on any product",
            description = "Admin-only endpoint to add, update, or soft-delete images on any product regardless of owner. Provides full image management capabilities."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Image updated successfully by admin",
                    content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Image added successfully by admin",
                    content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid image data"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Product or image not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/admin/{id}/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> manageProductImageAsAdmin(
            @Parameter(description = "Product ID", required = true, example = "1")
            @PathVariable("id") Long productId,
            
            @Parameter(description = "Image management data (add new image if imageId is null, update existing if imageId is provided)", required = true)
            @Valid @RequestBody ProductImageRequest request,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User admin = userService.findByUsername(userDetails.getUsername());
        ProductDetailResponse product = productService.manageProductImageAsAdmin(productId, request, admin.getUserId());

        String message;
        HttpStatus status;

        if (request.getImageId() != null) {
            if (request.getActive() != null && !request.getActive()) {
                message = "Image soft-deleted successfully by admin.";
            } else if (request.getActive() != null){
                message = "Image reactivated/updated successfully by admin.";
            }
            else {
                message = "Image updated successfully by admin.";
            }
            status = HttpStatus.OK;
        } else {
            message = "Image added successfully by admin.";
            status = HttpStatus.CREATED;
        }

        return ResponseEntity.status(status)
                .body(ApiResponse.<ProductDetailResponse>builder()
                        .status(status.toString())
                        .message(message)
                        .data(product)
                        .build());
    }
}