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
@RequestMapping("/api/v1/payos")
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

        if (!"PAID".equals(status) || !"00".equals(code)) {
            httpServletResponse.sendRedirect(failureUrl);
            return;
        }

        try {
            long orderCode = Long.parseLong(orderCodeStr);
            boolean isPaymentVerified = payOsService.verifyPaymentStatus(orderCode);
            if (isPaymentVerified) {
                try {
                    membershipService.activateSubscription(String.valueOf(orderCode));
                    httpServletResponse.sendRedirect(successUrl);
                } catch (Exception e) {
                    httpServletResponse.sendRedirect(successUrl);
                }
            } else {
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
