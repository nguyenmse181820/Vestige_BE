package se.vestige_be.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.UserMembershipDTO;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.dto.response.UserSubscriptionStatusResponse;
import se.vestige_be.pojo.MembershipPlan;
import se.vestige_be.pojo.UserMembership;
import se.vestige_be.service.MembershipService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/memberships")  // Use only the standard API path
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Membership Management", description = "APIs for managing user memberships and subscription plans")
public class MembershipController {

    private final MembershipService membershipService;

    @GetMapping("/plans")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get all membership plans")
    public ResponseEntity<ApiResponse<List<MembershipPlan>>> getAllPlans() {
        try {
            List<MembershipPlan> plans = membershipService.getAllPlans();
            return ResponseEntity.ok(ApiResponse.success("Membership plans retrieved successfully", plans));
        } catch (Exception e) {
            log.error("Error retrieving membership plans: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve membership plans: " + e.getMessage()));
        }
    }

    @GetMapping("/current")
    @PreAuthorize("hasRole('USER')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get current active membership")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<UserMembershipDTO>> getActiveMembership(@AuthenticationPrincipal UserDetails currentUser) {
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Authentication required to access membership information"));
            }
            
            Optional<UserMembershipDTO> activeMembership = membershipService.getActiveMembership(currentUser);

            return activeMembership
                    .map(membershipDTO -> ResponseEntity.ok(ApiResponse.success("Active membership found.", membershipDTO)))
                    .orElseGet(() -> ResponseEntity.ok(ApiResponse.success("No active membership found.", null)));
        } catch (Exception e) {
            log.error("Error retrieving active membership for user {}: {}", 
                currentUser != null ? currentUser.getUsername() : "unknown", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve membership information: " + e.getMessage()));
        }
    }

    @GetMapping("/my-subscription")
    @PreAuthorize("hasRole('USER')")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get full membership status (active and queued)")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<UserSubscriptionStatusResponse>> getFullMembershipStatus(@AuthenticationPrincipal UserDetails currentUser) {
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Authentication required."));
            }
            UserSubscriptionStatusResponse status = membershipService.getFullSubscriptionStatus(currentUser);
            return ResponseEntity.ok(ApiResponse.success("Full subscription status retrieved successfully.", status));
        } catch (Exception e) {
            log.error("Error retrieving full membership status for user {}: {}",
                    currentUser != null ? currentUser.getUsername() : "unknown", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve subscription status: " + e.getMessage()));
        }
    }

    @PostMapping("/subscription/{planId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<String>> subscribe(@PathVariable Long planId,
                                                         @AuthenticationPrincipal UserDetails currentUser) {

        if (currentUser == null) {
            return new ResponseEntity<>(ApiResponse.error("Unauthorized: User must be authenticated."), HttpStatus.UNAUTHORIZED);
        }
        try {
            log.info("User {} is subscribing to plan {}", currentUser.getUsername(), planId);
            String checkoutUrl = membershipService.subscribe(currentUser, planId);
            return ResponseEntity.ok(ApiResponse.success("Successfully created subscription payment link.", checkoutUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to create subscription: " + e.getMessage()));
        }
    }

    @DeleteMapping("/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelSubscription(@AuthenticationPrincipal UserDetails currentUser) {
        if (currentUser == null) {
            return new ResponseEntity<>(ApiResponse.error("Unauthorized: User must be authenticated."), HttpStatus.UNAUTHORIZED);
        }
        try {
            membershipService.cancelSubscription(currentUser);
            return ResponseEntity.ok(ApiResponse.success("Subscription cancelled successfully.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to cancel subscription: " + e.getMessage()));
        }
    }
}