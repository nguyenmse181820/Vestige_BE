package se.vestige_be.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.dto.request.OrderStatusUpdateRequest;
import se.vestige_be.service.MembershipService;
import se.vestige_be.service.PayOsService;
import se.vestige_be.service.OrderService;
import se.vestige_be.repository.TransactionRepository;
import se.vestige_be.pojo.Transaction;
import se.vestige_be.pojo.OrderItem;
import se.vestige_be.pojo.enums.TransactionStatus;
import se.vestige_be.pojo.enums.EscrowStatus;
import se.vestige_be.pojo.enums.OrderItemStatus;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/payos")
@RequiredArgsConstructor
@Slf4j
public class PayOsController {

    private final PayOsService payOsService;
    private final MembershipService membershipService;
    private final OrderService orderService;
    private final TransactionRepository transactionRepository;
    /**
     * Manual payment confirmation endpoint for frontend-initiated confirmation
     * Frontend calls this after user completes payment and gets redirected with payment params
     * Handles both membership and order payments
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
                // Check if it's a membership payment or order payment
                Optional<Transaction> transactionOpt = transactionRepository.findByPayosOrderCodeWithAllRelationships(orderCodeStr);
                
                if (transactionOpt.isPresent()) {
                    // It's an order payment
                    Transaction transaction = transactionOpt.get();
                    
                    try {
                        log.info("Payment verified successfully for order code: {}. Confirming order payment...", orderCodeStr);
                        
                        // Update transaction status
                        transaction.setStatus(TransactionStatus.PAID);
                        transaction.setEscrowStatus(EscrowStatus.HOLDING);
                        transaction.setPaidAt(LocalDateTime.now());
                        transactionRepository.save(transaction);
                        
                        // Confirm order payment through OrderService
                        orderService.confirmPayOsPayment(transaction.getOrderItem().getOrder().getOrderId(), transaction.getBuyer().getUserId(), orderCodeStr);
                        
                        log.info("Order payment confirmed successfully for order code: {}", orderCodeStr);
                        
                        return ResponseEntity.ok(ApiResponse.<String>builder()
                                .message("Payment confirmed and order processed successfully")
                                .data("Order code: " + orderCodeStr)
                                .build());
                                
                    } catch (Exception e) {
                        log.error("CRITICAL: Failed to confirm order payment for order code {}: {}", orderCodeStr, e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.<String>builder()
                                        .message("Payment verified but order confirmation failed: " + e.getMessage())
                                        .data("Order code: " + orderCodeStr)
                                        .build());
                    }
                } else {
                    // It's a membership payment
                    try {
                        log.info("Payment verified successfully for order code: {}. Activating subscription...", orderCodeStr);
                        membershipService.activateSubscription(orderCodeStr);
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

    /**
     * Handle PayOS payment cancellation
     * Called when user cancels payment on PayOS UI or payment times out
     */
    @PostMapping("/cancel-payment")
    public ResponseEntity<ApiResponse<String>> cancelPayment(
            @RequestParam(name = "orderCode") String orderCodeStr) {

        log.info("PayOS payment cancellation received - orderCode: {}", orderCodeStr);

        try {
            // Check if it's an order payment (has transaction)
            Optional<Transaction> transactionOpt = transactionRepository.findByPayosOrderCodeWithAllRelationships(orderCodeStr);
            
            if (transactionOpt.isPresent()) {
                // It's an order payment - cancel the order and restore product status
                Transaction transaction = transactionOpt.get();
                OrderItem orderItem = transaction.getOrderItem();
                
                log.info("Cancelling order payment for order code: {}, product: {}", orderCodeStr, orderItem.getProduct().getProductId());
                
                // Cancel the order item (this will restore product status)
                OrderStatusUpdateRequest updateRequest = OrderStatusUpdateRequest.builder()
                        .status("CANCELLED")
                        .notes("Payment cancelled by user")
                        .build();
                
                orderService.updateOrderItemStatus(orderItem.getOrder().getOrderId(), 
                                                   orderItem.getOrderItemId(), 
                                                   updateRequest, 
                                                   transaction.getBuyer().getUserId());
                
                log.info("Order payment cancelled successfully for order code: {}", orderCodeStr);
                
                return ResponseEntity.ok(ApiResponse.<String>builder()
                        .message("Order payment cancelled successfully")
                        .data("Order code: " + orderCodeStr)
                        .build());
                        
            } else {
                // It's a membership payment - cancel the pending membership
                try {
                    membershipService.cancelPendingSubscription(orderCodeStr);
                    log.info("Membership payment cancelled successfully for order code: {}", orderCodeStr);
                    
                    return ResponseEntity.ok(ApiResponse.<String>builder()
                            .message("Membership payment cancelled successfully")
                            .data("Order code: " + orderCodeStr)
                            .build());
                            
                } catch (Exception e) {
                    log.warn("Failed to cancel pending membership for order code {}: {}", orderCodeStr, e.getMessage());
                    
                    return ResponseEntity.ok(ApiResponse.<String>builder()
                            .message("Payment cancellation processed (membership may not exist)")
                            .data("Order code: " + orderCodeStr)
                            .build());
                }
            }

        } catch (Exception e) {
            log.error("Error processing payment cancellation for order code {}: {}", orderCodeStr, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<String>builder()
                            .message("Error processing payment cancellation: " + e.getMessage())
                            .data("Order code: " + orderCodeStr)
                            .build());
        }
    }

}
