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
import se.vestige_be.pojo.MembershipPlan;
import se.vestige_be.pojo.UserMembership;
import se.vestige_be.service.MembershipService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/memberships")
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Membership Management", description = "APIs for managing user memberships and subscription plans")
public class MembershipController {

    private final MembershipService membershipService;

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<MembershipPlan>>> getAllPlans() {
        try {
            List<MembershipPlan> plans = membershipService.getAllPlans();
            return ResponseEntity.ok(ApiResponse.success("Membership plans retrieved successfully", plans));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve membership plans: " + e.getMessage()));
        }
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<UserMembershipDTO>> getActiveMembership(@AuthenticationPrincipal UserDetails currentUser) {
        Optional<UserMembershipDTO> activeMembership = membershipService.getActiveMembership(currentUser);

        return activeMembership
                .map(membershipDTO -> ResponseEntity.ok(ApiResponse.success("Active membership found.", membershipDTO)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success("No active membership found.", null)));
    }

    @PostMapping("/subscribe/{planId}")
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