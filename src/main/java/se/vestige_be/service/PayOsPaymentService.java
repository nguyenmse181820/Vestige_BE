package se.vestige_be.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.request.OrderCreateRequest;
import se.vestige_be.dto.response.OrderDetailResponse;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.pojo.*;
import se.vestige_be.pojo.enums.*;
import se.vestige_be.repository.*;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.ItemData;
import vn.payos.type.PaymentData;
import vn.payos.type.PaymentLinkData;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOsPaymentService {

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final OfferRepository offerRepository;
    private final FeeTierService feeTierService;
    private final PayOsService payOsService;
    private final OrderService orderService;

    /**
     * Create payment for single product order using PayOS
     */
    @Transactional
    public PaymentResponse createPayment(Long buyerId, OrderCreateRequest request) {
        try {
            if (request.getItems().size() != 1) {
                throw new BusinessLogicException("Only single product orders are supported");
            }

            OrderCreateRequest.OrderItemRequest itemRequest = request.getItems().getFirst();
            User buyer = userRepository.findById(buyerId)
                    .orElseThrow(() -> new BusinessLogicException("Buyer not found"));

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new BusinessLogicException("Product not found"));

            UserAddress shippingAddress = userAddressRepository.findById(request.getShippingAddressId())
                    .orElseThrow(() -> new BusinessLogicException("Shipping address not found"));

            validatePurchase(buyer, product, shippingAddress);

            // Add debugging to check initial product status
            log.info("BEFORE ORDER CREATION - Product {} status: {}", product.getProductId(), product.getStatus());

            BigDecimal itemPrice = calculateItemPrice(itemRequest, product, buyerId);
            BigDecimal platformFee = feeTierService.calculatePlatformFee(itemPrice, product.getSeller());
            BigDecimal feePercentage = feeTierService.getFeePercentageForSeller(product.getSeller());

            // Generate PayOS order code
            String orderCode = generateOrderCode(System.currentTimeMillis());
            
            // Set PayOS payment method in request
            request.setPaymentMethod(PaymentMethod.PAYOS);
            
            // Create order using the main OrderService method to handle product status properly
            OrderDetailResponse orderResponse = orderService.createOrder(request, buyerId);
            
            // Get the created order
            Order order = orderRepository.findById(orderResponse.getOrderId())
                    .orElseThrow(() -> new BusinessLogicException("Failed to retrieve created order"));

            // CRITICAL FIX: Update transactions with PayOS orderCode
            // The main createOrder method doesn't set payosOrderCode, so we need to update it
            for (OrderItem orderItem : order.getOrderItems()) {
                Transaction transaction = transactionRepository.findByOrderItemOrderItemId(orderItem.getOrderItemId())
                        .orElseThrow(() -> new BusinessLogicException("Transaction not found for order item"));
                
                log.info("BEFORE UPDATE: Transaction ID: {}, Current payosOrderCode: '{}'", 
                        transaction.getTransactionId(), transaction.getPayosOrderCode());
                        
                transaction.setPayosOrderCode(orderCode);
                Transaction savedTransaction = transactionRepository.save(transaction);
                
                log.info("AFTER UPDATE: Transaction ID: {}, Stored payosOrderCode: '{}', Save successful: {}", 
                        savedTransaction.getTransactionId(), 
                        savedTransaction.getPayosOrderCode(),
                        savedTransaction.getPayosOrderCode() != null && savedTransaction.getPayosOrderCode().equals(orderCode));
                        
                // Verification: immediately try to find the transaction by orderCode to ensure it was saved correctly
                Optional<Transaction> verificationOpt = transactionRepository.findByPayosOrderCodeWithAllRelationships(orderCode);
                log.info("VERIFICATION: Can find transaction by orderCode '{}': {}", orderCode, verificationOpt.isPresent());
                if (!verificationOpt.isPresent()) {
                    log.error("CRITICAL: Transaction was saved but cannot be found by orderCode immediately after save!");
                }
            }

            // Add debugging to check product status after order creation
            Product refreshedProduct = productRepository.findById(product.getProductId()).orElse(product);
            log.info("AFTER ORDER CREATION - Product {} status: {}, PayOS orderCode: {}", 
                    refreshedProduct.getProductId(), refreshedProduct.getStatus(), orderCode);

            // Create PayOS payment link
            String checkoutUrl = createPayOsPaymentLink(order, refreshedProduct, orderCode);

            // Add debugging to verify product status is now PENDING_PAYMENT
            Product finalProduct = productRepository.findById(refreshedProduct.getProductId()).orElse(refreshedProduct);
            log.info("FINAL PRODUCT STATUS CHECK - Product {} status: {}", finalProduct.getProductId(), finalProduct.getStatus());

            return PaymentResponse.builder()
                    .orderId(order.getOrderId())
                    .transactionId(order.getOrderItems().getFirst().getOrderItemId())
                    .productTitle(finalProduct.getTitle())
                    .amount(order.getTotalAmount())
                    .platformFee(order.getOrderItems().getFirst().getPlatformFee())
                    .sellerAmount(order.getTotalAmount().subtract(order.getOrderItems().getFirst().getPlatformFee()))
                    .checkoutUrl(checkoutUrl)
                    .status("PENDING_PAYMENT")
                    .build();

        } catch (Exception e) {
            log.error("Error creating PayOS payment: {}", e.getMessage(), e);
            throw new BusinessLogicException("Failed to create payment: " + e.getMessage());
        }
    }

    /**
     * Create PayOS payment link for order
     */
    private String createPayOsPaymentLink(Order order, Product product, String orderCode) {
        try {
            PayOS payOS = payOsService.createPayOSInstance();
            
            ItemData item = ItemData.builder()
                    .name(truncateString(product.getTitle(), 50))
                    .quantity(1)
                    .price(order.getTotalAmount().intValue())
                    .build();



            String description = createPaymentDescription(product.getTitle(), order.getBuyer().getUsername());
            String returnUrl = payOsService.getFrontendUrl() + "/checkout/success?orderId=" + order.getOrderId();
//            String returnUrl = "http://localhost:3000/checkout/success?orderId=" + order.getOrderId();
            String cancelUrl = payOsService.getFrontendUrl() + "/payment-cancel?orderCode=" + orderCode;

            PaymentData paymentData = PaymentData.builder()
                    .orderCode(Long.parseLong(orderCode))
                    .amount(order.getTotalAmount().intValue())
                    .description(description)
                    .items(Collections.singletonList(item))
                    .cancelUrl(cancelUrl)
                    .returnUrl(returnUrl)
                    .build();

            CheckoutResponseData result = payOS.createPaymentLink(paymentData);
            
            log.info("Created PayOS payment link for order {}: orderCode={}, checkoutUrl={}", 
                    order.getOrderId(), orderCode, result.getCheckoutUrl());
            
            return result.getCheckoutUrl();
            
        } catch (Exception e) {
            log.error("Failed to create PayOS payment link for order {}: {}", 
                    order.getOrderId(), e.getMessage());
            throw new BusinessLogicException("Failed to create payment link: " + e.getMessage());
        }
    }

    /**
     * Validate purchase requirements
     */
    private void validatePurchase(User buyer, Product product, UserAddress shippingAddress) {
        if (!ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new BusinessLogicException("Product is not available for purchase");
        }

        if (product.getSeller().getUserId().equals(buyer.getUserId())) {
            throw new BusinessLogicException("Cannot purchase your own product");
        }

        if (!shippingAddress.getUser().getUserId().equals(buyer.getUserId())) {
            throw new BusinessLogicException("Shipping address does not belong to buyer");
        }

        if (!"active".equals(buyer.getAccountStatus())) {
            throw new BusinessLogicException("Buyer account is not active");
        }

        if (!"active".equals(product.getSeller().getAccountStatus())) {
            throw new BusinessLogicException("Seller account is not active");
        }
    }

    /**
     * Calculate item price based on offer or regular price
     */
    private BigDecimal calculateItemPrice(OrderCreateRequest.OrderItemRequest itemRequest, Product product, Long buyerId) {
        if (itemRequest.getOfferId() != null) {
            Offer offer = offerRepository.findById(itemRequest.getOfferId())
                    .orElseThrow(() -> new BusinessLogicException("Offer not found"));

            if (!offer.getProduct().getProductId().equals(product.getProductId())) {
                throw new BusinessLogicException("Offer does not match product");
            }
            if (!OfferStatus.ACCEPTED.equals(offer.getStatus())) {
                throw new BusinessLogicException("Offer is not accepted");
            }
            if (!offer.getBuyer().getUserId().equals(buyerId)) {
                throw new BusinessLogicException("Offer does not belong to buyer");
            }

            return offer.getAmount();
        }
        return product.getPrice();
    }

    /**
     * Generate unique order code for PayOS
     */
    private String generateOrderCode(Long timestamp) {
        // Generate a consistent 11-digit orderCode
        String orderCode = String.valueOf(timestamp).substring(5) + String.format("%03d", (int)(timestamp % 1000));
        log.debug("Generated orderCode: '{}' from timestamp: {}", orderCode, timestamp);
        return orderCode;
    }

    /**
     * Create payment description within PayOS limits
     */
    private String createPaymentDescription(String productTitle, String username) {
        final int MAX_LENGTH = 25;
        String description = productTitle + " - " + username;
        
        if (description.length() <= MAX_LENGTH) {
            return description;
        }
        
        if (productTitle.length() <= MAX_LENGTH) {
            return productTitle;
        }
        
        return productTitle.substring(0, MAX_LENGTH - 3) + "...";
    }

    /**
     * Truncate string to max length
     */
    private String truncateString(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Response DTO for PayOS payment
     */
    @Getter
    @Builder
    public static class PaymentResponse {
        private Long orderId;
        private Long transactionId;
        private String productTitle;
        private BigDecimal amount;
        private BigDecimal platformFee;
        private BigDecimal sellerAmount;
        private String checkoutUrl;
        private String status;
    }
}
