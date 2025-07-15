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
     * Manual payment confirmation endpoint for frontend-initiated confirmation
     * Frontend calls this after user completes payment and gets redirected with payment params
     */
    @PostMapping("/confirm-payment")
    public ResponseEntity<ApiResponse<String>> confirmPayment(
            @RequestParam(name = "code") String code,
            @RequestParam(name = "status") String status,
            @RequestParam(name = "orderCode") String orderCodeStr) {

        log.info("Manual payment confirmation received - code: {}, status: {}, orderCode: {}", code, status, orderCodeStr);

        if (!"PAID".equals(status) || !"00".equals(code)) {
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .message("Payment was not successful")
                    .data("Status: " + status + ", Code: " + code)
                    .build());
        }

        try {
            long orderCode = Long.parseLong(orderCodeStr);
            boolean isPaymentVerified = payOsService.verifyPaymentStatus(orderCode);
            if (isPaymentVerified) {
                try {
                    log.info("Payment verified successfully for order code: {}. Activating subscription...", orderCodeStr);
                    membershipService.activateSubscription(orderCodeStr); // Pass original string to preserve leading zeros
                    log.info("Subscription activated successfully for order code: {}", orderCodeStr);
                    
                    return ResponseEntity.ok(ApiResponse.<String>builder()
                            .message("Payment confirmed and subscription activated successfully")
                            .data("Order code: " + orderCodeStr)
                            .build());
                            
                } catch (Exception e) {
                    log.error("CRITICAL: Failed to activate subscription for order code {}: {}", orderCodeStr, e.getMessage(), e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.<String>builder()
                                    .message("Payment verified but subscription activation failed: " + e.getMessage())
                                    .data("Order code: " + orderCodeStr)
                                    .build());
                }
            } else {
                log.warn("Payment verification failed for order code: {}", orderCode);
                return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                        .message("Payment verification failed")
                        .data("Order code: " + orderCode)
                        .build());
            }

        } catch (NumberFormatException e) {
            log.error("Invalid orderCode format in payment confirmation: {}", orderCodeStr);
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .message("Invalid order code format")
                    .data("Order code: " + orderCodeStr)
                    .build());
        } catch (Exception e) {
            log.error("Error processing payment confirmation for order code {}: {}", orderCodeStr, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<String>builder()
                            .message("Error processing payment confirmation: " + e.getMessage())
                            .data("Order code: " + orderCodeStr)
                            .build());
        }
    }

}
