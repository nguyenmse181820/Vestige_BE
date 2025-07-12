package se.vestige_be.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.service.MembershipService;
import se.vestige_be.service.PayOsService;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/api/payos")
@RequiredArgsConstructor
@Slf4j
public class PayOsController {

    private final PayOsService payOsService;
    private final MembershipService membershipService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @PostMapping("/webhook")
    @Deprecated(since = "2024-12-11", forRemoval = true)
    public ResponseEntity<ApiResponse<String>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-PayOS-Signature", required = false) String signature) {
        try {
            if (signature == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<String>builder()
                                .message("Missing signature header")
                                .build());
            }

            boolean isValidSignature = payOsService.verifyWebhookSignature(signature, payload);
            if (!isValidSignature) {
                log.error("PayOS webhook signature verification failed");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<String>builder()
                                .message("Invalid webhook signature")
                                .build());
            }

            // Parse webhook data
            PayOsService.PayOsWebhookData webhookData = payOsService.parseWebhookData(payload);
            
            if ("PAID".equals(webhookData.getStatus()) || "SUCCESS".equals(webhookData.getStatus())) {
                log.info("Processing successful payment for order code: {}", webhookData.getOrderCode());
                
                // Activate subscription via MembershipService
                membershipService.activateSubscription(webhookData.getOrderCode());
                
                log.info("Successfully activated subscription for order code: {}", webhookData.getOrderCode());
                
                return ResponseEntity.ok(ApiResponse.<String>builder()
                        .message("Payment processed successfully")
                        .data("OK")
                        .build());
                
            } else if ("CANCELLED".equals(webhookData.getStatus()) || "FAILED".equals(webhookData.getStatus())) {
                log.info("Payment failed or cancelled for order code: {}", webhookData.getOrderCode());
                
                // Handle failed payment - could update membership status to FAILED
                membershipService.handleFailedPayment(webhookData.getOrderCode());
                
                return ResponseEntity.ok(ApiResponse.<String>builder()
                        .message("Payment failure processed")
                        .data("OK")
                        .build());
                
            } else {
                log.info("PayOS webhook received with status: {} for order code: {}", 
                        webhookData.getStatus(), webhookData.getOrderCode());
                
                // Other statuses like PENDING, PROCESSING - just acknowledge
                return ResponseEntity.ok(ApiResponse.<String>builder()
                        .message("Webhook received")
                        .data("OK")
                        .build());
            }

        } catch (Exception e) {
            log.error("Error processing PayOS webhook: {}", e.getMessage(), e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<String>builder()
                            .message("Webhook processing failed - will retry")
                            .build());
        }
    }

    /**
     * Health check endpoint for PayOS webhook
     */
    @GetMapping("/webhook/health")
    public ResponseEntity<ApiResponse<String>> webhookHealthCheck() {
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .message("PayOS webhook endpoint is healthy")
                .data("OK")
                .build());
    }

    /**
     * Handles PayOS payment callback (Return URL method)
     * This endpoint receives the user after payment completion and performs server-side verification
     */
    @GetMapping("/payment-callback")
    public void paymentCallback(
            @RequestParam(name = "code") String code,
            @RequestParam(name = "status") String status,
            @RequestParam(name = "orderCode") String orderCodeStr,
            jakarta.servlet.http.HttpServletResponse httpServletResponse) throws java.io.IOException {

        // Frontend URLs for redirection
        String successUrl = frontendUrl + "/payment/success";
        String failureUrl = frontendUrl + "/payment/failure";

        log.info("Payment callback received: code={}, status={}, orderCode={}", code, status, orderCodeStr);

        // Step 1: Quick check of the status from the URL. If not "PAID", redirect to failure page immediately.
        if (!"PAID".equals(status) || !"00".equals(code)) {
            log.warn("Payment callback received with non-paid status for orderCode: {}. Status: {}, Code: {}", 
                    orderCodeStr, status, code);
            httpServletResponse.sendRedirect(failureUrl);
            return;
        }

        try {
            long orderCode = Long.parseLong(orderCodeStr);

            // Step 2: Verify the transaction status with the PayOS server (MOST IMPORTANT FOR SECURITY)
            boolean isPaymentVerified = payOsService.verifyPaymentStatus(orderCode);

            if (isPaymentVerified) {
                // Step 3: If verification is successful, execute the business logic
                log.info("Processing successful payment for order code: {}", orderCode);
                
                try {
                    membershipService.activateSubscription(String.valueOf(orderCode));
                    log.info("Successfully activated subscription for order code: {}", orderCode);
                    
                    // Redirect the user to the success page
                    httpServletResponse.sendRedirect(successUrl);
                    
                } catch (Exception e) {
                    log.error("Failed to activate subscription for order code {}: {}", orderCode, e.getMessage());
                    // Even if activation fails, we verified payment - redirect to success but log the issue
                    httpServletResponse.sendRedirect(successUrl);
                }
                
            } else {
                // If verification fails, treat the transaction as invalid
                log.error("Server-side verification failed for a PAID status callback. OrderCode: {}", orderCode);
                httpServletResponse.sendRedirect(failureUrl);
            }

        } catch (NumberFormatException e) {
            log.error("Invalid orderCode format in payment callback: {}", orderCodeStr);
            httpServletResponse.sendRedirect(failureUrl);
        } catch (Exception e) {
            log.error("Error processing payment callback for order code {}: {}", orderCodeStr, e.getMessage(), e);
            httpServletResponse.sendRedirect(failureUrl);
        }
    }
}
