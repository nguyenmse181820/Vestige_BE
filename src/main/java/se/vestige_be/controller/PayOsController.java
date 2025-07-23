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
                // Try to find transaction with the original orderCode format first
                log.info("DEBUG: Looking for transaction with original orderCode: '{}'", orderCodeStr);
                Optional<Transaction> transactionOpt = transactionRepository.findByPayosOrderCodeWithAllRelationships(orderCodeStr);
                
                // If not found, try with leading zeros format (PayOS sometimes adds leading zeros)
                if (transactionOpt.isEmpty() && orderCodeStr.length() < 11) {
                    String paddedOrderCode = String.format("%011d", orderCode);
                    log.info("Transaction not found with orderCode '{}', trying with leading zeros: '{}'", orderCodeStr, paddedOrderCode);
                    transactionOpt = transactionRepository.findByPayosOrderCodeWithAllRelationships(paddedOrderCode);
                }
                
                // If still not found, try removing leading zeros (in case stored without but received with)
                if (transactionOpt.isEmpty() && orderCodeStr.startsWith("0")) {
                    String unPaddedOrderCode = String.valueOf(orderCode);
                    log.info("Transaction not found with padded orderCode, trying without leading zeros: '{}'", unPaddedOrderCode);
                    transactionOpt = transactionRepository.findByPayosOrderCodeWithAllRelationships(unPaddedOrderCode);
                }
                
                if (transactionOpt.isPresent()) {
                    // It's an order payment
                    Transaction transaction = transactionOpt.get();
                    
                    try {
                        log.info("Payment verified successfully for order code: {}. Confirming order payment...", orderCodeStr);
                        log.info("DEBUG: Found transaction with ID: {}, stored orderCode: '{}'", 
                                transaction.getTransactionId(), transaction.getPayosOrderCode());
                        
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
                    // Transaction not found - could be membership payment OR orderCode format issue
                    // Let's also check if there's an order that was recently created for this product
                    // by checking if there are any recent orders with PENDING status
                    
                    log.info("DEBUG: Transaction not found after trying all orderCode formats. Original: '{}', Padded: '{}', Numeric: '{}'", 
                            orderCodeStr, 
                            orderCodeStr.length() < 11 ? String.format("%011d", orderCode) : "N/A",
                            String.valueOf(orderCode));
                    log.info("DEBUG: Assuming this is a membership payment and attempting to activate subscription...");
                    
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
                        
                        // Additional debugging info for orderCode format issues
                        log.error("DEBUG: OrderCode format analysis - received: '{}', length: {}", orderCodeStr, orderCodeStr.length());
                        
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
            // Try to find transaction with the original orderCode format first
            Optional<Transaction> transactionOpt = transactionRepository.findByPayosOrderCodeWithAllRelationships(orderCodeStr);
            
            // If not found, try with leading zeros format (PayOS sometimes adds leading zeros)
            if (transactionOpt.isEmpty()) {
                try {
                    long orderCode = Long.parseLong(orderCodeStr);
                    if (orderCodeStr.length() < 11) {
                        String paddedOrderCode = String.format("%011d", orderCode);
                        log.info("Transaction not found with orderCode '{}', trying with leading zeros: '{}'", orderCodeStr, paddedOrderCode);
                        transactionOpt = transactionRepository.findByPayosOrderCodeWithAllRelationships(paddedOrderCode);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid orderCode format: {}", orderCodeStr);
                }
            }
            
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
