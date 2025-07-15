package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.CreateReviewRequest;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.dto.response.PagedResponse;
import se.vestige_be.dto.response.ReviewResponse;
import se.vestige_be.dto.response.SellerRatingResponse;
import se.vestige_be.pojo.User;
import se.vestige_be.service.ReviewService;
import se.vestige_be.service.UserService;
import se.vestige_be.util.PaginationUtils;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/reviews")
@Tag(name = "Review Management", 
     description = "Review and rating system for sellers. Allows buyers to rate and review sellers after completing transactions.")
@RequiredArgsConstructor
@Slf4j
public class ReviewController {

    private final ReviewService reviewService;
    private final UserService userService;

    @Operation(
            summary = "Create a review for a seller",
            description = "Create a review and rating for a seller after completing a transaction. Only buyers can review sellers for transactions they participated in."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Review created successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request - transaction not eligible for review or already reviewed"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Unauthorized - not the buyer of this transaction"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Transaction not found"
            )
    })
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @Parameter(description = "Review creation request", required = true)
            @Valid @RequestBody CreateReviewRequest request,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        
        ReviewResponse review = reviewService.createReview(request, user.getUserId());
        
        return ResponseEntity.status(201).body(ApiResponse.<ReviewResponse>builder()
                .message("Review created successfully")
                .data(review)
                .build());
    }

    @Operation(
            summary = "Get all reviews for a seller",
            description = "Get paginated list of all reviews for a specific seller, ordered by creation date (newest first)."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Reviews retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Seller not found"
            )
    })
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewResponse>>> getSellerReviews(
            @Parameter(description = "Seller ID", required = true, example = "1")
            @PathVariable Long sellerId,
            
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            
            @Parameter(description = "Number of items per page", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            
            @Parameter(description = "Field to sort by", example = "createdAt",
                      schema = @Schema(allowableValues = {"createdAt", "rating"}))
            @RequestParam(defaultValue = "createdAt") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc",
                      schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);
        PagedResponse<ReviewResponse> reviews = reviewService.getSellerReviews(sellerId, pageable);
        
        return ResponseEntity.ok(ApiResponse.<PagedResponse<ReviewResponse>>builder()
                .message("Seller reviews retrieved successfully")
                .data(reviews)
                .build());
    }

    @Operation(
            summary = "Get comprehensive seller rating information",
            description = "Get detailed rating information for a seller including average rating, total reviews, rating breakdown, and recent reviews."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Seller rating information retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Seller not found"
            )
    })
    @GetMapping("/seller/{sellerId}/rating")
    public ResponseEntity<ApiResponse<SellerRatingResponse>> getSellerRating(
            @Parameter(description = "Seller ID", required = true, example = "1")
            @PathVariable Long sellerId) {

        SellerRatingResponse rating = reviewService.getSellerRating(sellerId);
        
        return ResponseEntity.ok(ApiResponse.<SellerRatingResponse>builder()
                .message("Seller rating information retrieved successfully")
                .data(rating)
                .build());
    }

    @Operation(
            summary = "Get reviews made by current user",
            description = "Get all reviews that the current user has made as a buyer."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "User reviews retrieved successfully"
            )
    })
    @GetMapping("/my-reviews")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewResponse>>> getUserReviews(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            
            @Parameter(description = "Number of items per page", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            
            @Parameter(description = "Field to sort by", example = "createdAt",
                      schema = @Schema(allowableValues = {"createdAt", "rating"}))
            @RequestParam(defaultValue = "createdAt") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc",
                      schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(defaultValue = "desc") String sortDir,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);
        PagedResponse<ReviewResponse> reviews = reviewService.getUserReviews(user.getUserId(), pageable);
        
        return ResponseEntity.ok(ApiResponse.<PagedResponse<ReviewResponse>>builder()
                .message("Your reviews retrieved successfully")
                .data(reviews)
                .build());
    }

    @Operation(
            summary = "Check if transaction can be reviewed",
            description = "Check if the current user can review a specific transaction."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Review eligibility checked successfully"
            )
    })
    @GetMapping("/transaction/{transactionId}/can-review")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> canReviewTransaction(
            @Parameter(description = "Transaction ID", required = true, example = "1")
            @PathVariable Long transactionId,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        boolean canReview = reviewService.canReviewTransaction(transactionId, user.getUserId());
        
        return ResponseEntity.ok(ApiResponse.<Map<String, Boolean>>builder()
                .message("Review eligibility checked")
                .data(Map.of("canReview", canReview))
                .build());
    }

    @Operation(
            summary = "Get review for a transaction",
            description = "Get the review for a specific transaction if it exists."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Review retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Review not found for this transaction"
            )
    })
    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> getReviewForTransaction(
            @Parameter(description = "Transaction ID", required = true, example = "1")
            @PathVariable Long transactionId) {

        Optional<ReviewResponse> reviewOpt = reviewService.getReviewForTransaction(transactionId);

        return reviewOpt.map(reviewResponse -> ResponseEntity.ok(ApiResponse.<ReviewResponse>builder()
                .message("Review retrieved successfully")
                .data(reviewResponse)
                .build())).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
