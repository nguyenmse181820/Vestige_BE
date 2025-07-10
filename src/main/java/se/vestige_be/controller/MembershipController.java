package se.vestige_be.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.pojo.MembershipPlan;
import se.vestige_be.pojo.ProductBoost;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.UserMembership;
import se.vestige_be.service.MembershipService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/memberships")
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Membership Management", description = "APIs for managing user memberships and subscription plans")
public class MembershipController {

    private final MembershipService membershipService;

    /**
     * Get all available membership plans
     */
    @GetMapping("/plans")
    public ResponseEntity<ApiResponse> getAllPlans() {
        try {
            List<MembershipPlan> plans = membershipService.getAllPlans();
            return ResponseEntity.ok(ApiResponse.success("Membership plans retrieved successfully", plans));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve membership plans: " + e.getMessage()));
        }
    }

    /**
     * Get current user's active membership
     */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse> getCurrentMembership(@AuthenticationPrincipal User currentUser) {
        try {
            Optional<UserMembership> activeMembership = membershipService.getActiveMembership(currentUser);
            if (activeMembership.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success("Active membership retrieved successfully",
                        activeMembership.get()));
            } else {
                return ResponseEntity.ok(ApiResponse.success("No active membership found", null));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve current membership: " + e.getMessage()));
        }
    }

    /**
     * Subscribe to a membership plan. Returns a Stripe Checkout URL.
     */
    @PostMapping("/subscribe/{planId}")
    public ResponseEntity<ApiResponse> subscribe(@PathVariable Long planId,
                                                 @AuthenticationPrincipal User currentUser) {
        try {
            // The service returns the Stripe Checkout URL as a String
            String checkoutUrl = membershipService.subscribe(currentUser, planId);

            // Return the URL in a structured response for the frontend to use
            Map<String, String> responseData = Map.of("checkoutUrl", checkoutUrl);

            return ResponseEntity.ok(ApiResponse.success("Stripe checkout session created. Please redirect to the provided URL.", responseData));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to create subscription: " + e.getMessage()));
        }
    }

    /**
     * Cancel current subscription
     */
    @DeleteMapping("/cancel")
    public ResponseEntity<ApiResponse> cancelSubscription(@AuthenticationPrincipal User currentUser) {
        try {
            membershipService.cancelSubscription(currentUser);
            return ResponseEntity.ok(ApiResponse.success("Subscription cancellation requested successfully. It will be deactivated at the end of the current billing period.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to cancel subscription: " + e.getMessage()));
        }
    }

    /**
     * Boost a product using membership benefits
     */
    @PostMapping("/boost/{productId}")
    public ResponseEntity<ApiResponse> boostProduct(@PathVariable Long productId,
                                                    @AuthenticationPrincipal User currentUser) {
        try {
            ProductBoost boost = membershipService.boostProduct(currentUser, productId);
            return ResponseEntity.ok(ApiResponse.success("Product boosted successfully", boost));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to boost product: " + e.getMessage()));
        }
    }
}
