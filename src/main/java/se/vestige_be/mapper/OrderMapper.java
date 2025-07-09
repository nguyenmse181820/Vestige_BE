package se.vestige_be.mapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import se.vestige_be.dto.response.OrderDetailResponse;
import se.vestige_be.dto.response.OrderListResponse;
import se.vestige_be.pojo.*;
import se.vestige_be.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        Map<String, List<OrderDetailResponse.OrderItemDetail>> itemsBySeller = order.getOrderItems().stream()
                .map(this::convertToOrderItemDetail)
                .collect(Collectors.groupingBy(item -> item.getSeller().getUserId().toString()));

        List<OrderDetailResponse.OrderItemDetail> itemDetails = order.getOrderItems().stream()
                .map(this::convertToOrderItemDetail)
                .collect(Collectors.toList());

        User buyer = order.getBuyer();
        OrderDetailResponse.BuyerInfo buyerInfo = OrderDetailResponse.BuyerInfo.builder()
                .userId(buyer.getUserId())
                .username(buyer.getUsername())
                .firstName(buyer.getFirstName())
                .lastName(buyer.getLastName())
                .phoneNumber(buyer.getPhoneNumber())
                .email(buyer.getEmail())
                .build();

        return OrderDetailResponse.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .totalShippingFee(BigDecimal.ZERO)
                .totalPlatformFee(calculateTotalPlatformFee(order))
                .totalItems(order.getOrderItems().size())
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
        List<OrderListResponse.OrderItemSummary> itemSummaries = order.getOrderItems().stream()
            .map(this::convertToOrderItemSummary)
            .collect(Collectors.toList());

        String overallEscrowStatus = calculateOverallEscrowStatus(order);
        log.debug("Order {} - Overall escrow status calculated as: {}", order.getOrderId(), overallEscrowStatus);

        return OrderListResponse.builder()
            .orderId(order.getOrderId())
            .status(order.getStatus().name())
            .totalAmount(order.getTotalAmount())
            .totalShippingFee(BigDecimal.ZERO)
            .totalItems(order.getOrderItems().size())
            .uniqueSellers((int) order.getOrderItems().stream()
                .map(item -> item.getSeller().getUserId())
                .distinct().count())
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
        log.debug("Converting order item {} - status: {}, escrowStatus: {}", 
            orderItem.getOrderItemId(), orderItem.getStatus(), orderItem.getEscrowStatus());
        
        return OrderDetailResponse.OrderItemDetail.builder()
            .orderItemId(orderItem.getOrderItemId())
            .price(orderItem.getPrice())
            .platformFee(orderItem.getPlatformFee())
            .feePercentage(orderItem.getFeePercentage())
            .status(orderItem.getStatus().name())
            .escrowStatus(orderItem.getEscrowStatus().name())
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
            .sellerIsLegitProfile(orderItem.getSeller().getIsLegitProfile())
            .escrowStatus(orderItem.getEscrowStatus().name())
            .itemStatus(orderItem.getStatus().name())
            .build();
    }
    
    /**
     * Convert product to product info response
     */
    public OrderDetailResponse.ProductInfo convertToProductInfo(Product product) {
        String primaryImageUrl = null;
        if (!product.getImages().isEmpty()) {
            primaryImageUrl = product.getImages().get(0).getImageUrl();
        }

        return OrderDetailResponse.ProductInfo.builder()
            .productId(product.getProductId())
            .title(product.getTitle())
            .description(product.getDescription())
            .condition(product.getCondition().name())
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
            .isLegitProfile(seller.getIsLegitProfile())
            .sellerRating(seller.getSellerRating())
            .sellerReviewsCount(seller.getSellerReviewsCount())
            .build();
    }
    
    /**
     * Convert order item to transaction info response
     */
    public OrderDetailResponse.TransactionInfo convertToTransactionInfo(OrderItem orderItem) {
        Transaction transaction = getTransactionForOrderItem(orderItem.getOrderItemId());
        return OrderDetailResponse.TransactionInfo.builder()
            .transactionId(transaction.getTransactionId())
            .trackingNumber(transaction.getTrackingNumber())
            .trackingUrl(transaction.getTrackingUrl())
            .shippedAt(transaction.getShippedAt())
            .deliveredAt(transaction.getDeliveredAt())
            .buyerProtectionEligible(true)
            .build();
    }
    
    /**
     * Convert user address to shipping address info response
     */
    public OrderDetailResponse.ShippingAddressInfo convertToShippingAddressInfo(UserAddress address) {
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
        return order.getOrderItems().stream()
            .map(OrderItem::getPlatformFee)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Calculate overall escrow status for an order based on its items
     */
    private String calculateOverallEscrowStatus(Order order) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return "NONE";
        }
        
        // Count items by escrow status
        Map<String, Long> statusCounts = order.getOrderItems().stream()
            .collect(Collectors.groupingBy(
                item -> item.getEscrowStatus().name(),
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
     * Get transaction for a given order item
     */
    private Transaction getTransactionForOrderItem(Long orderItemId) {
        return transactionRepository.findByOrderItemOrderItemId(orderItemId)
                .orElseThrow(() -> new IllegalStateException("Transaction not found for order item: " + orderItemId));
    }
    
    /**
     * Format transaction data to summary map for admin views
     */
    public Map<String, Object> formatTransactionSummary(Transaction transaction) {
        OrderItem orderItem = transaction.getOrderItem();
        Product product = orderItem != null ? orderItem.getProduct() : null;
        
        Map<String, Object> buyerMap = new HashMap<>();
        buyerMap.put("userId", transaction.getBuyer().getUserId());
        buyerMap.put("username", transaction.getBuyer().getUsername());
        
        Map<String, Object> sellerMap = new HashMap<>();
        sellerMap.put("userId", transaction.getSeller().getUserId());
        sellerMap.put("username", transaction.getSeller().getUsername());
        
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
        result.put("orderId", orderItem != null ? orderItem.getOrder().getOrderId() : null);
        result.put("productTitle", product != null ? product.getTitle() : "Unknown Product");
        result.put("dispute", disputeMap);
        
        return result;
    }
}
