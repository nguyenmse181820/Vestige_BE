package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.AdminCreateUserRequest;
import se.vestige_be.dto.request.AdminUpdateUserRequest;
import se.vestige_be.dto.request.UpdateProfileRequest;
import se.vestige_be.dto.response.*;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.service.UserService;
import se.vestige_be.util.PaginationUtils;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", 
     description = "Complete user management API including public user discovery and admin operations. " +
                   "Provides role-based access for user profiles, admin user management, and comprehensive user statistics.")
@AllArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PagedResponse<UserListResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "joinedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isLegitProfile,
            @RequestParam(required = false) String accountStatus) {

        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);
        PagedResponse<UserListResponse> users = userService.getAllUsers(pageable, search, isLegitProfile, accountStatus);

        return ResponseEntity.ok(ApiResponse.<PagedResponse<UserListResponse>>builder()
                .status(HttpStatus.OK.toString())
                .message("Users retrieved successfully")
                .data(users)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(@PathVariable Long id) {
        UserProfileResponse user = userService.getUserById(id);

        return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                .status(HttpStatus.OK.toString())
                .message("User retrieved successfully")
                .data(user)
                .build());
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        UserProfileResponse profile = userService.getUserProfile(userDetails.getUsername());

        return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                .status(HttpStatus.OK.toString())
                .message("Profile retrieved successfully")
                .data(profile)
                .build());
    }

    @PostMapping("/create-user")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> adminCreateUser(
            @Valid @RequestBody AdminCreateUserRequest request,
            @AuthenticationPrincipal UserDetails adminDetails) {
        try {
            UserProfileResponse userProfile = userService.createUserByAdmin(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.<UserProfileResponse>builder()
                            .status(HttpStatus.CREATED.toString())
                            .message("User created successfully by admin.")
                            .data(userProfile)
                            .build());
        } catch (BusinessLogicException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<UserProfileResponse>builder()
                            .status(HttpStatus.BAD_REQUEST.toString())
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<UserProfileResponse>builder()
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.toString())
                            .message("An unexpected error occurred while creating the user.")
                            .build());
        }
    }

    @PatchMapping("/profile")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UserProfileResponse updatedProfile = userService.updateProfile(userDetails.getUsername(), request);

        return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                .status(HttpStatus.OK.toString())
                .message("Profile updated successfully")
                .data(updatedProfile)
                .build());
    }

    // Admin endpoints for user management
    @Operation(
            summary = "[ADMIN] Update user information",
            description = "Admin-only endpoint to update any user's information including profile data, roles, verification status, and account settings. Supports partial updates - only provided fields will be updated."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "User updated successfully",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid user data or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> adminUpdateUser(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable Long userId,
            
            @Parameter(description = "User update data - all fields are optional for partial updates", required = true)
            @Valid @RequestBody AdminUpdateUserRequest request,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails adminDetails) {
        try {
            UserProfileResponse updatedUser = userService.adminUpdateUser(userId, request);
            return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                    .status(HttpStatus.OK.toString())
                    .message("User updated successfully by admin")
                    .data(updatedUser)
                    .build());
        } catch (BusinessLogicException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<UserProfileResponse>builder()
                            .status(HttpStatus.BAD_REQUEST.toString())
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<UserProfileResponse>builder()
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.toString())
                            .message("An unexpected error occurred while updating the user")
                            .build());
        }
    }

    @Operation(
            summary = "[ADMIN] Deactivate user account",
            description = "Admin-only endpoint to deactivate a user account. This performs a soft delete by setting the user as inactive and suspended rather than permanently deleting the account to preserve data integrity."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "User deactivated successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> adminDeleteUser(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable Long userId,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails adminDetails) {
        try {
            userService.adminDeleteUser(userId);
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .status(HttpStatus.OK.toString())
                    .message("User deactivated successfully")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Void>builder()
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.toString())
                            .message("An unexpected error occurred while deactivating the user")
                            .build());
        }
    }

    @Operation(
            summary = "[ADMIN] Get user statistics",
            description = "Admin-only endpoint to retrieve comprehensive statistics for a specific user including order metrics, product listings, account activity, and financial summaries."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "User statistics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = UserStatisticsResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/{userId}/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserStatisticsResponse>> getUserStatistics(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable Long userId) {
        try {
            UserStatisticsResponse statistics = userService.getUserStatistics(userId);
            return ResponseEntity.ok(ApiResponse.<UserStatisticsResponse>builder()
                    .status(HttpStatus.OK.toString())
                    .message("User statistics retrieved successfully")
                    .data(statistics)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<UserStatisticsResponse>builder()
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.toString())
                            .message("An unexpected error occurred while retrieving user statistics")
                            .build());
        }
    }

    @Operation(
            summary = "[ADMIN] Get all users with statistics",
            description = "Admin-only endpoint to retrieve paginated list of all users with their comprehensive statistics including order counts, financial metrics, and account activity summaries."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Users with statistics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<UserStatisticsResponse>>> getAllUsersWithStatistics(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Field to sort by", example = "joinedDate",
                      schema = @Schema(allowableValues = {"joinedDate", "totalOrders", "totalOrderValue", "username"}))
            @RequestParam(defaultValue = "joinedDate") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc",
                      schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(defaultValue = "desc") String sortDir,
            
            @Parameter(description = "Search by username or email")
            @RequestParam(required = false) String search,
            
            @Parameter(description = "Filter by legit profile status")
            @RequestParam(required = false) Boolean isLegitProfile,
            
            @Parameter(description = "Filter by account status")
            @RequestParam(required = false) String accountStatus) {

        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);
        PagedResponse<UserStatisticsResponse> userStatistics = userService.getAllUsersWithStatistics(
                pageable, search, isLegitProfile, accountStatus);

        return ResponseEntity.ok(ApiResponse.<PagedResponse<UserStatisticsResponse>>builder()
                .status(HttpStatus.OK.toString())
                .message("User statistics retrieved successfully")
                .data(userStatistics)
                .build());
    }

    @Operation(
            summary = "[ADMIN] Get all users for admin management",
            description = "Admin-only endpoint to retrieve paginated list of all users with enhanced filtering and management capabilities. Provides basic user information optimized for admin user management interface."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Users retrieved successfully for admin",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<UserListResponse>>> adminGetAllUsers(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Field to sort by", example = "joinedDate",
                      schema = @Schema(allowableValues = {"joinedDate", "username", "email", "accountStatus"}))
            @RequestParam(defaultValue = "joinedDate") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc",
                      schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(defaultValue = "desc") String sortDir,
            
            @Parameter(description = "Search by username or email")
            @RequestParam(required = false) String search,
            
            @Parameter(description = "Filter by legit profile status")
            @RequestParam(required = false) Boolean isLegitProfile,
            
            @Parameter(description = "Filter by account status")
            @RequestParam(required = false) String accountStatus) {

        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);
        PagedResponse<UserListResponse> users = userService.getAllUsers(pageable, search, isLegitProfile, accountStatus);

        return ResponseEntity.ok(ApiResponse.<PagedResponse<UserListResponse>>builder()
                .status(HttpStatus.OK.toString())
                .message("Users retrieved successfully for admin")
                .data(users)
                .build());
    }

    @Operation(
            summary = "[ADMIN] Bulk update user status",
            description = "Admin-only endpoint to update account status or verification for multiple users at once. Useful for batch operations like bulk verification or status changes."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Bulk update completed successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid bulk update request"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/admin/bulk-update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> bulkUpdateUsers(
            @Parameter(description = "List of user IDs to update", required = true)
            @RequestParam List<Long> userIds,
            
            @Parameter(description = "New account status to apply")
            @RequestParam(required = false) String accountStatus,
            
            @Parameter(description = "New verification status to apply")
            @RequestParam(required = false) Boolean isVerified,
            
            @Parameter(description = "New legit profile status to apply")
            @RequestParam(required = false) Boolean isLegitProfile,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails adminDetails) {
        
        try {
            userService.bulkUpdateUsers(userIds, accountStatus, isVerified, isLegitProfile);
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .status(HttpStatus.OK.toString())
                    .message("Bulk user update completed successfully")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<String>builder()
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.toString())
                            .message("Failed to perform bulk update: " + e.getMessage())
                            .build());
        }
    }

    @Operation(
            summary = "[ADMIN] Get user activity summary",
            description = "Admin-only endpoint to get a summary of recent user activity across the platform including registrations, logins, and transactions."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "User activity summary retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/activity-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getUserActivitySummary(
            @Parameter(description = "Number of days to look back for activity", example = "30")
            @RequestParam(defaultValue = "30") int days) {
        
        Object activitySummary = userService.getUserActivitySummary(days);
        
        return ResponseEntity.ok(ApiResponse.builder()
                .status(HttpStatus.OK.toString())
                .message("User activity summary retrieved successfully")
                .data(activitySummary)
                .build());
    }
}