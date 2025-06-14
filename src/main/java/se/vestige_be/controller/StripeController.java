package se.vestige_be.controller;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.Balance;
import com.stripe.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.pojo.User;
import se.vestige_be.service.StripeService;
import se.vestige_be.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeController {

    private final StripeService stripeService;
    private final UserService userService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/onboard-seller")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> onboardSeller(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            // Create Stripe account if it doesn't exist
            if (user.getStripeAccountId() == null) {
                Account account = stripeService.createExpressAccount(user);
                user.setStripeAccountId(account.getId());
                userService.save(user);
                log.info("Created Stripe account {} for user {}", account.getId(), user.getUsername());
            }

            // Check if account setup is already complete
            if (stripeService.isAccountSetupComplete(user.getStripeAccountId())) {
                return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                        .message("Stripe account is already set up")
                        .data(Map.of(
                                "accountId", user.getStripeAccountId(),
                                "setupComplete", "true"
                        ))
                        .build());
            }

            // Create onboarding link
            String refreshUrl = frontendUrl + "/stripe/refresh";
            String returnUrl = frontendUrl + "/stripe/return";
            AccountLink accountLink = stripeService.createAccountLink(
                    user.getStripeAccountId(), refreshUrl, returnUrl);

            return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                    .message("Stripe onboarding URL created")
                    .data(Map.of(
                            "url", accountLink.getUrl(),
                            "accountId", user.getStripeAccountId()
                    ))
                    .build());

        } catch (StripeException e) {
            log.error("Stripe error for user {}: {}", user.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Map<String, String>>builder()
                            .message("Error creating Stripe account link: " + e.getMessage())
                            .build());
        }
    }

    @GetMapping("/account-status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAccountStatus(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        if (user.getStripeAccountId() == null) {
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .message("No Stripe account found")
                    .data(Map.of("hasAccount", false))
                    .build());
        }

        try {
            boolean setupComplete = stripeService.isAccountSetupComplete(user.getStripeAccountId());

            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .message("Account status retrieved")
                    .data(Map.of(
                            "hasAccount", true,
                            "accountId", user.getStripeAccountId(),
                            "setupComplete", setupComplete
                    ))
                    .build());

        } catch (StripeException e) {
            log.error("Error checking account status for user {}: {}", user.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Map<String, Object>>builder()
                            .message("Error checking account status: " + e.getMessage())
                            .build());
        }
    }

    @PostMapping("/refresh-onboarding")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> refreshOnboarding(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        if (user.getStripeAccountId() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Map<String, String>>builder()
                            .message("No Stripe account found. Please start onboarding first.")
                            .build());
        }

        try {
            String refreshUrl = frontendUrl + "/stripe/refresh";
            String returnUrl = frontendUrl + "/stripe/return";
            AccountLink accountLink = stripeService.createAccountLink(
                    user.getStripeAccountId(), refreshUrl, returnUrl);

            return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                    .message("New onboarding URL created")
                    .data(Map.of("url", accountLink.getUrl()))
                    .build());

        } catch (StripeException e) {
            log.error("Error refreshing onboarding for user {}: {}", user.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Map<String, String>>builder()
                            .message("Error refreshing onboarding: " + e.getMessage())
                            .build());
        }
    }

    @GetMapping("/platform-balance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlatformBalance() {
        try {
            Balance balance = stripeService.getPlatformBalance();

            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .message("Platform balance retrieved")
                    .data(Map.of(
                            "available", balance.getAvailable(),
                            "pending", balance.getPending()
                    ))
                    .build());

        } catch (StripeException e) {
            log.error("Error retrieving platform balance: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Map<String, Object>>builder()
                            .message("Error retrieving balance: " + e.getMessage())
                            .build());
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        log.info("Received webhook payload: {}", payload.substring(0, Math.min(100, payload.length())));

        try {
            if (sigHeader == null) {
                log.warn("Missing Stripe-Signature header");
                return ResponseEntity.badRequest().body("Missing signature");
            }

            Event event = stripeService.validateWebhook(payload, sigHeader, webhookSecret);
            stripeService.processWebhookEvent(event);

            log.info("Successfully processed webhook: {}", event.getType());
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Webhook error: {}", e.getMessage(), e);
            return ResponseEntity.ok("Error logged"); // Return 200 to prevent retries
        }
    }
}