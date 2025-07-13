package se.vestige_be.mapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import se.vestige_be.dto.response.OrderDetailResponse;
import se.vestige_be.dto.response.OrderListResponse;
import se.vestige_be.pojo.*;
import se.vestige_be.pojo.enums.OrderItemStatus;
import se.vestige_be.pojo.enums.TransactionStatus;
import se.vestige_be.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Mapper class for converting order domain objects to response DTOs
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMapper {
    
    private final TransactionRepository transactionRepository;
    
    /**
     * Convert order entity to detailed response
     */
    public OrderDetailResponse convertToDetailResponse(Order order) {
        if (order == null) {
            log.warn("Order is null when converting to detail response");
            return null;
        }
        
        // Safely handle order items with null checks
        List<OrderDetailResponse.OrderItemDetail> itemDetails = new ArrayList<>();
        Map<String, List<OrderDetailResponse.OrderItemDetail>> itemsBySeller = new HashMap<>();
        
        if (order.getOrderItems() != null) {
            itemDetails = order.getOrderItems().stream()
                    .map(this::convertToOrderItemDetail)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            itemsBySeller = itemDetails.stream()
                    .filter(item -> item.getSeller() != null && item.getSeller().getUserId() != null)
                    .collect(Collectors.groupingBy(item -> item.getSeller().getUserId().toString()));
        }

        // Safely handle buyer information
        OrderDetailResponse.BuyerInfo buyerInfo = null;
        if (order.getBuyer() != null) {
            User buyer = order.getBuyer();
            buyerInfo = OrderDetailResponse.BuyerInfo.builder()
                    .userId(buyer.getUserId())
                    .username(buyer.getUsername())
                    .firstName(buyer.getFirstName())
                    .lastName(buyer.getLastName())
                    .phoneNumber(buyer.getPhoneNumber())
                    .email(buyer.getEmail())
                    .build();
        }

        return OrderDetailResponse.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus() != null ? order.getStatus().name() : "UNKNOWN")
                .totalAmount(order.getTotalAmount())
                .totalShippingFee(BigDecimal.ZERO)
                .totalPlatformFee(calculateTotalPlatformFee(order))
                .totalItems(order.getOrderItems() != null ? order.getOrderItems().size() : 0)
                .uniqueSellers(itemsBySeller.size())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .shippedAt(order.getShippedAt())
                .deliveredAt(order.getDeliveredAt())
                .stripePaymentIntentId(order.getStripePaymentIntentId())
                .orderItems(itemDetails)
                .buyer(buyerInfo)
                .shippingAddress(convertToShippingAddressInfo(order.getShippingAddress()))
                .itemsBySeller(itemsBySeller)
                .build();
    }

    /**
     * Convert order entity to list response
     */
    public OrderListResponse convertToListResponse(Order order) {
        if (order == null) {
            log.warn("Order is null when converting to list response");
            return null;
        }
        
        // Safely handle order items
        List<OrderListResponse.OrderItemSummary> itemSummaries = new ArrayList<>();
        int totalItems = 0;
        int uniqueSellers = 0;
        
        if (order.getOrderItems() != null) {
            itemSummaries = order.getOrderItems().stream()
                .map(this::convertToOrderItemSummary)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            totalItems = order.getOrderItems().size();
            uniqueSellers = (int) order.getOrderItems().stream()
                .map(item -> item.getSeller() != null ? item.getSeller().getUserId() : null)
                .filter(Objects::nonNull)
                .distinct().count();
        }

        String overallEscrowStatus = calculateOverallEscrowStatus(order);
        log.debug("Order {} - Overall escrow status calculated as: {}", order.getOrderId(), overallEscrowStatus);

        return OrderListResponse.builder()
            .orderId(order.getOrderId())
            .status(order.getStatus() != null ? order.getStatus().name() : "UNKNOWN")
            .totalAmount(order.getTotalAmount())
            .totalShippingFee(BigDecimal.ZERO)
            .totalItems(totalItems)
            .uniqueSellers(uniqueSellers)
            .createdAt(order.getCreatedAt())
            .paidAt(order.getPaidAt())
            .deliveredAt(order.getDeliveredAt())
            .totalPlatformFee(calculateTotalPlatformFee(order))
            .itemSummaries(itemSummaries)
            .overallEscrowStatus(overallEscrowStatus)
            .build();
    }
    
    /**
     * Convert order item to detail response
     */
    public OrderDetailResponse.OrderItemDetail convertToOrderItemDetail(OrderItem orderItem) {
        if (orderItem == null) {
            log.warn("OrderItem is null when converting to detail");
            return null;
        }
        
        log.debug("Converting order item {} - status: {}, escrowStatus: {}", 
            orderItem.getOrderItemId(), 
            orderItem.getStatus(), 
            orderItem.getEscrowStatus());
        
        return OrderDetailResponse.OrderItemDetail.builder()
            .orderItemId(orderItem.getOrderItemId())
            .price(orderItem.getPrice())
            .platformFee(orderItem.getPlatformFee())
            .feePercentage(orderItem.getFeePercentage())
            .status(orderItem.getStatus() != null ? orderItem.getStatus().name() : "UNKNOWN")
            .escrowStatus(orderItem.getEscrowStatus() != null ? orderItem.getEscrowStatus().name() : "UNKNOWN")
            .product(convertToProductInfo(orderItem.getProduct()))
            .seller(convertToSellerInfo(orderItem.getSeller()))
            .transaction(convertToTransactionInfo(orderItem))
            .build();
    }
    
    /**
     * Convert order item to summary response
     */
    public OrderListResponse.OrderItemSummary convertToOrderItemSummary(OrderItem orderItem) {
        // Get primary image URL from product
        String primaryImageUrl = null;
        try {
            if (orderItem.getProduct() != null && 
                orderItem.getProduct().getImages() != null && 
                !orderItem.getProduct().getImages().isEmpty()) {
                primaryImageUrl = orderItem.getProduct().getImages().get(0).getImageUrl();
                log.debug("Product {} has {} images, primary image: {}", 
                    orderItem.getProduct().getProductId(), 
                    orderItem.getProduct().getImages().size(), 
                    primaryImageUrl);
            } else {
                log.debug("Product {} has no images loaded", 
                    orderItem.getProduct() != null ? orderItem.getProduct().getProductId() : "null");
            }
        } catch (Exception e) {
            log.warn("Failed to load images for product {}: {}", 
                orderItem.getProduct() != null ? orderItem.getProduct().getProductId() : "null", 
                e.getMessage());
        }
        
        return OrderListResponse.OrderItemSummary.builder()
            .productId(orderItem.getProduct().getProductId())
            .productTitle(orderItem.getProduct().getTitle())
            .productImage(primaryImageUrl)
            .price(orderItem.getPrice())
            .sellerUsername(orderItem.getSeller().getUsername())
            .sellerIsVerified(orderItem.getSeller().getIsVerified())
            .escrowStatus(orderItem.getEscrowStatus().name())
            .itemStatus(orderItem.getStatus().name())
            .build();
    }
    
    /**
     * Convert product to product info response
     */
    public OrderDetailResponse.ProductInfo convertToProductInfo(Product product) {
        if (product == null) {
            return OrderDetailResponse.ProductInfo.builder()
                .productId(null)
                .title("Unknown Product")
                .description("Product information not available")
                .build();
        }
        
        // Safely get primary image URL with null checks
        String primaryImageUrl = null;
        try {
            List<ProductImage> images = product.getImages();
            if (images != null && !images.isEmpty()) {
                primaryImageUrl = images.get(0).getImageUrl();
            }
        } catch (Exception e) {
            log.warn("Failed to load images for product {}: {}", product.getProductId(), e.getMessage());
        }

        return OrderDetailResponse.ProductInfo.builder()
            .productId(product.getProductId())
            .title(product.getTitle())
            .description(product.getDescription())
            .condition(product.getCondition() != null ? product.getCondition().name() : "UNKNOWN")
            .size(product.getSize())
            .color(product.getColor())
            .primaryImageUrl(primaryImageUrl)
            .shippingFee(BigDecimal.ZERO)
            .categoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null)
            .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
            .brandId(product.getBrand() != null ? product.getBrand().getBrandId() : null)
            .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
            .build();
    }
    
    /**
     * Convert user to seller info response
     */
    public OrderDetailResponse.SellerInfo convertToSellerInfo(User seller) {
        return OrderDetailResponse.SellerInfo.builder()
            .userId(seller.getUserId())
            .username(seller.getUsername())
            .firstName(seller.getFirstName())
            .lastName(seller.getLastName())
            .isVerified(seller.getIsVerified())
            .sellerRating(seller.getSellerRating())
            .sellerReviewsCount(seller.getSellerReviewsCount())
            .build();
    }
    
    /**
     * Convert order item to transaction info response
     */
    public OrderDetailResponse.TransactionInfo convertToTransactionInfo(OrderItem orderItem) {
        if (orderItem == null) {
            log.warn("OrderItem is null when converting to transaction info");
            return OrderDetailResponse.TransactionInfo.builder()
                .transactionId(null)
                .canReview(false)
                .hasReview(false)
                .buyerProtectionEligible(false)
                .build();
        }
        
        Transaction transaction = getTransactionForOrderItem(orderItem.getOrderItemId());
        if (transaction == null) {
            log.warn("Transaction not found for order item: {}", orderItem.getOrderItemId());
            return OrderDetailResponse.TransactionInfo.builder()
                .transactionId(null)
                .canReview(false)
                .hasReview(false)
                .buyerProtectionEligible(false)
                .build();
        }
        
        // Check if transaction can be reviewed
        boolean canReview = canTransactionBeReviewed(transaction);
        
        // Get existing review if any - safely handle lazy loading
        boolean hasReview = false;
        OrderDetailResponse.ReviewInfo reviewInfo = null;
        try {
            List<Review> reviews = transaction.getReviews();
            if (reviews != null && !reviews.isEmpty()) {
                hasReview = true;
                Review review = reviews.get(0);
                reviewInfo = OrderDetailResponse.ReviewInfo.builder()
                        .reviewId(review.getReviewId())
                        .rating(review.getRating())
                        .comment(review.getComment())
                        .authenticityRating(review.getAuthenticityRating())
                        .authenticityComment(review.getAuthenticityComment())
                        .createdAt(review.getCreatedAt())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to load reviews for transaction {}: {}", transaction.getTransactionId(), e.getMessage());
            hasReview = false;
        }
        
        return OrderDetailResponse.TransactionInfo.builder()
            .transactionId(transaction.getTransactionId())
            .trackingNumber(transaction.getTrackingNumber())
            .trackingUrl(transaction.getTrackingUrl())
            .shippedAt(transaction.getShippedAt())
            .deliveredAt(transaction.getDeliveredAt())
            .buyerProtectionEligible(transaction.getBuyerProtectionEligible() != null ? transaction.getBuyerProtectionEligible() : true)
            .canReview(canReview)
            .hasReview(hasReview)
            .review(reviewInfo)
            .build();
    }
    
    /**
     * Check if a transaction can be reviewed
     */
    private boolean canTransactionBeReviewed(Transaction transaction) {
        if (transaction == null) {
            return false;
        }
        
        // Must be delivered
        if (transaction.getStatus() != TransactionStatus.DELIVERED) {
            return false;
        }
        
        // Check order item status as well
        OrderItem orderItem = transaction.getOrderItem();
        if (orderItem == null || orderItem.getStatus() != OrderItemStatus.DELIVERED) {
            return false;
        }
        
        // Must not have existing review - safely handle lazy loading
        try {
            List<Review> reviews = transaction.getReviews();
            if (reviews != null && !reviews.isEmpty()) {
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to check reviews for transaction {}: {}", transaction.getTransactionId(), e.getMessage());
            // If we can't check reviews, assume false for safety
            return false;
        }
        
        return true;
    }
    
    /**
     * Convert user address to shipping address info response
     */
    public OrderDetailResponse.ShippingAddressInfo convertToShippingAddressInfo(UserAddress address) {
        if (address == null) {
            return null;
        }
        
        return OrderDetailResponse.ShippingAddressInfo.builder()
            .addressId(address.getAddressId())
            .addressLine1(address.getAddressLine1())
            .addressLine2(address.getAddressLine2())
            .city(address.getCity())
            .state(address.getState())
            .postalCode(address.getPostalCode())
            .country(address.getCountry())
            .build();
    }
    
    /**
     * Calculate total platform fee for an order
     */
    private BigDecimal calculateTotalPlatformFee(Order order) {
        if (order == null || order.getOrderItems() == null) {
            return BigDecimal.ZERO;
        }
        
        return order.getOrderItems().stream()
            .map(OrderItem::getPlatformFee)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Calculate overall escrow status for an order based on its items
     */
    private String calculateOverallEscrowStatus(Order order) {
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return "NONE";
        }
        
        // Count items by escrow status with null safety
        Map<String, Long> statusCounts = order.getOrderItems().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(
                item -> item.getEscrowStatus() != null ? item.getEscrowStatus().name() : "UNKNOWN",
                Collectors.counting()
            ));
        
        long totalItems = order.getOrderItems().size();
        
        // If all items have same status, return that status
        for (Map.Entry<String, Long> entry : statusCounts.entrySet()) {
            if (entry.getValue() == totalItems) {
                return entry.getKey();
            }
        }
        
        // Handle mixed statuses based on priority
        if (statusCounts.getOrDefault("DISPUTE", 0L) > 0) {
            return "DISPUTE";
        }
        
        if (statusCounts.getOrDefault("HOLDING", 0L) > 0) {
            return "HOLDING";
        }
        
        if (statusCounts.getOrDefault("RELEASED", 0L) > 0) {
            return "PARTIAL_RELEASED";
        }
        
        return "MIXED";
    }
    
    /**
     * Get transaction for a given order item with enhanced loading
     */
    private Transaction getTransactionForOrderItem(Long orderItemId) {
        if (orderItemId == null) {
            log.warn("OrderItemId is null when getting transaction");
            return null;
        }
        
        try {
            // Try enhanced method first for better performance
            Optional<Transaction> transactionOpt = transactionRepository.findByOrderItemOrderItemIdWithAllRelationships(orderItemId);
            if (transactionOpt.isPresent()) {
                return transactionOpt.get();
            }
            
            // Fallback to basic method
            return transactionRepository.findByOrderItemOrderItemId(orderItemId)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Failed to get transaction for order item {}: {}", orderItemId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Format transaction data to summary map for admin views
     */
    public Map<String, Object> formatTransactionSummary(Transaction transaction) {
        if (transaction == null) {
            log.warn("Transaction is null when formatting summary");
            return new HashMap<>();
        }
        
        OrderItem orderItem = transaction.getOrderItem();
        Product product = orderItem != null ? orderItem.getProduct() : null;
        
        Map<String, Object> buyerMap = new HashMap<>();
        if (transaction.getBuyer() != null) {
            buyerMap.put("userId", transaction.getBuyer().getUserId());
            buyerMap.put("username", transaction.getBuyer().getUsername());
        } else {
            buyerMap.put("userId", null);
            buyerMap.put("username", "Unknown Buyer");
        }
        
        Map<String, Object> sellerMap = new HashMap<>();
        if (transaction.getSeller() != null) {
            sellerMap.put("userId", transaction.getSeller().getUserId());
            sellerMap.put("username", transaction.getSeller().getUsername());
        } else {
            sellerMap.put("userId", null);
            sellerMap.put("username", "Unknown Seller");
        }
        
        Map<String, Object> disputeMap = new HashMap<>();
        disputeMap.put("status", transaction.getDisputeStatus());
        disputeMap.put("reason", transaction.getDisputeReason());
        
        Map<String, Object> result = new HashMap<>();
        result.put("transactionId", transaction.getTransactionId());
        result.put("amount", transaction.getAmount());
        result.put("status", transaction.getStatus());
        result.put("escrowStatus", transaction.getEscrowStatus());
        result.put("createdAt", transaction.getCreatedAt());
        result.put("buyer", buyerMap);
        result.put("seller", sellerMap);
        result.put("orderItemId", orderItem != null ? orderItem.getOrderItemId() : null);
        result.put("orderId", orderItem != null && orderItem.getOrder() != null ? orderItem.getOrder().getOrderId() : null);
        result.put("productTitle", product != null ? product.getTitle() : "Unknown Product");
        result.put("dispute", disputeMap);
        
        return result;
    }
}
