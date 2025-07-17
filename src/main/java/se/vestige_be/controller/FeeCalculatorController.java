package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.service.FeeTierService;
import se.vestige_be.service.UserService;
import se.vestige_be.pojo.User;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/fee-calculator")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fee Calculator", description = "API for calculating platform fees and seller earnings")
public class FeeCalculatorController {

    private final FeeTierService feeTierService;
    private final UserService userService;

    @Operation(
            summary = "Calculate seller earnings for an item",
            description = "Calculate how much a seller will receive for an item based on their fee tier and the gross amount"
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/seller-earnings")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateSellerEarnings(
            @Parameter(description = "Gross amount in VND", required = true, example = "1000000")
            @RequestParam BigDecimal grossAmount,
            
            @Parameter(description = "Seller ID (optional, defaults to current user)", example = "1")
            @RequestParam(required = false) Long sellerId,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        if (grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, Object>>builder()
                    .message("Gross amount must be greater than 0")
                    .build());
        }

        User currentUser = userService.findByUsername(userDetails.getUsername());
        User seller = currentUser;
        
        // If sellerId is provided and user is admin, allow calculation for any seller
        if (sellerId != null && !sellerId.equals(currentUser.getUserId())) {
            if (currentUser.getRole().getName().equals("ADMIN")) {
                seller = userService.findById(sellerId);
            } else {
                return ResponseEntity.status(403).body(ApiResponse.<Map<String, Object>>builder()
                        .message("You can only calculate earnings for your own items")
                        .build());
            }
        }

        try {
            BigDecimal platformFee = feeTierService.calculatePlatformFee(grossAmount, seller);
            BigDecimal sellerAmount = feeTierService.calculateSellerAmount(grossAmount, seller);
            BigDecimal feePercentage = feeTierService.getFeePercentageForSeller(seller);

            Map<String, Object> result = new HashMap<>();
            result.put("grossAmount", grossAmount);
            result.put("platformFee", platformFee);
            result.put("sellerAmount", sellerAmount);
            result.put("feePercentage", feePercentage);
            result.put("sellerId", seller.getUserId());
            result.put("sellerUsername", seller.getUsername());
            
            // TODO: Implement membership logic
            /*
            if (seller.getActiveMembership() != null) {
                result.put("membershipPlan", seller.getActiveMembership().getPlan().getName());
                result.put("feeTier", seller.getActiveMembership().getPlan().getFeeTier().getName());
            } else {
                result.put("membershipPlan", "Basic");
                result.put("feeTier", "Basic");
            }
            */
            result.put("membershipPlan", "Basic");
            result.put("feeTier", "Basic");

            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .message("Seller earnings calculated successfully")
                    .data(result)
                    .build());

        } catch (Exception e) {
            log.error("Error calculating seller earnings: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(ApiResponse.<Map<String, Object>>builder()
                    .message("Error calculating seller earnings: " + e.getMessage())
                    .build());
        }
    }

    @Operation(
            summary = "Get current user's fee tier information",
            description = "Get information about the current user's fee tier and membership plan"
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/fee-tier")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFeeTierInfo(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        BigDecimal feePercentage = feeTierService.getFeePercentageForSeller(user);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getUserId());
        result.put("username", user.getUsername());
        result.put("feePercentage", feePercentage);
        
        // TODO: Implement membership logic
        /*
        if (user.getActiveMembership() != null) {
            result.put("membershipPlan", user.getActiveMembership().getPlan().getName());
            result.put("feeTier", user.getActiveMembership().getPlan().getFeeTier().getName());
            result.put("membershipStartDate", user.getActiveMembership().getStartDate());
            result.put("membershipEndDate", user.getActiveMembership().getEndDate());
        } else {
            result.put("membershipPlan", "Basic");
            result.put("feeTier", "Basic");
            result.put("membershipStartDate", null);
            result.put("membershipEndDate", null);
        }
        */
        result.put("membershipPlan", "Basic");
        result.put("feeTier", "Basic");
        result.put("membershipStartDate", null);
        result.put("membershipEndDate", null);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .message("Fee tier information retrieved successfully")
                .data(result)
                .build());
    }
}
