package se.vestige_be.controller;

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
import se.vestige_be.dto.request.UpdateProfileRequest;
import se.vestige_be.dto.response.*;
import se.vestige_be.service.UserService;
import se.vestige_be.util.PaginationUtils;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User", description = "API for user management")
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
}