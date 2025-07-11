package se.vestige_be.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.service.MembershipService;
import se.vestige_be.service.PayOsService;

@RestController
@RequestMapping("/api/payos")
@RequiredArgsConstructor
@Slf4j
public class PayOsController {

    private final PayOsService payOsService;
    private final MembershipService membershipService;

    /**
     * Handles PayOS webhook notifications for payment events
     */
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<String>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-PayOS-Signature", required = false) String signature) {

        log.info("Received PayOS webhook payload: {}", payload.substring(0, Math.min(100, payload.length())));

        try {
            // Verify webhook signature
            if (signature == null) {
                log.warn("Missing X-PayOS-Signature header");
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
            
            log.info("PayOS webhook data parsed: orderCode={}, status={}", 
                    webhookData.getOrderCode(), webhookData.getStatus());

            // Process payment based on status
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
            
            // Return 500 to trigger PayOS retry mechanism
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
}
