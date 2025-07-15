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
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.RatingRequest;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.dto.response.PagedResponse;
import se.vestige_be.dto.response.RatingResponse;
import se.vestige_be.dto.response.SystemRatingStatsResponse;
import se.vestige_be.service.SystemRatingService;
import se.vestige_be.service.UserService;
import se.vestige_be.util.PaginationUtils;

/**
 * Controller for system/project ratings.
 * This allows users to rate the overall system/project, not specific users.
 * Each user can submit one rating for the entire system.
 */
@RestController
@RequestMapping("/api/ratings")
@Tag(name = "System Rating Management", 
     description = "System/project rating system. Allows users to rate the overall system and view all ratings.")
@RequiredArgsConstructor
public class SystemRatingController {

    private final SystemRatingService systemRatingService;
    private final UserService userService; // To resolve username to user ID

    @Operation(
            summary = "Submit or update system rating",
            description = "Submit or update a rating for the overall system. Each user can only have one rating."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RatingResponse> submitSystemRating(@Valid @RequestBody RatingRequest request) {
        Long currentUserId = getCurrentUserId();
        RatingResponse response = systemRatingService.createOrUpdateSystemRating(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get current user's system rating",
            description = "Get the current user's system rating if it exists."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/my-rating")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RatingResponse> getMySystemRating() {
        Long currentUserId = getCurrentUserId();
        return systemRatingService.getUserSystemRating(currentUserId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Get system rating statistics",
            description = "Get statistical information about system ratings including average rating, total ratings, and rating breakdown. This endpoint is public and does not require authentication."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "System rating statistics retrieved successfully"
            )
    })
    @GetMapping("/stats")
    public ResponseEntity<SystemRatingStatsResponse> getSystemRatingStatistics() {
        SystemRatingStatsResponse stats = systemRatingService.getSystemRatingStatistics();
        return ResponseEntity.ok(stats);
    }

    @Operation(
            summary = "Get all system ratings",
            description = "Get all system ratings with user information and comments. This endpoint is public and does not require authentication."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "All system ratings retrieved successfully"
            )
    })
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<PagedResponse<RatingResponse>>> getAllRatings(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            
            @Parameter(description = "Number of items per page", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            
            @Parameter(description = "Field to sort by", example = "createdAt",
                      schema = @Schema(allowableValues = {"createdAt", "updatedAt", "rating"}))
            @RequestParam(defaultValue = "createdAt") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc",
                      schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);
        PagedResponse<RatingResponse> ratings = systemRatingService.getAllRatings(pageable);
        
        return ResponseEntity.ok(ApiResponse.<PagedResponse<RatingResponse>>builder()
                .message("All system ratings retrieved successfully")
                .data(ratings)
                .build());
    }

    private Long getCurrentUserId() {
        String username = ((UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername();
        return userService.findByUsername(username).getUserId();
    }
}
