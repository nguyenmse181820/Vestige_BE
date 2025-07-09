package se.vestige_be.service;

import com.stripe.model.Dispute;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Join;
import se.vestige_be.dto.request.OrderCreateRequest;
import se.vestige_be.dto.request.OrderStatusUpdateRequest;
import se.vestige_be.dto.response.*;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.exception.ResourceNotFoundException;
import se.vestige_be.exception.UnauthorizedException;
import se.vestige_be.pojo.*;
import se.vestige_be.pojo.enums.*;
import se.vestige_be.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final OfferRepository offerRepository;
    private final TransactionRepository transactionRepository;
    private final FeeTierService feeTierService;
    private final StripeService stripeService;

    @Transactional
    public OrderDetailResponse createOrder(OrderCreateRequest request, Long buyerId) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found with ID: " + buyerId));
        UserAddress shippingAddress = userAddressRepository.findById(request.getShippingAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Shipping address not found with ID: " + request.getShippingAddressId()));
        if (!shippingAddress.getUser().getUserId().equals(buyerId)) {
            throw new UnauthorizedException("Shipping address does not belong to the buyer");
        }
        List<OrderItemData> itemDataList = validateAndProcessItems(request.getItems(), buyerId);
        BigDecimal totalAmount = itemDataList.stream()
                .map(OrderItemData::getItemPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .buyer(buyer)
                .totalAmount(totalAmount)
                .shippingAddress(shippingAddress)
                .paymentMethod(request.getPaymentMethod())
                .status(OrderStatus.PENDING)
                .build();
        order = orderRepository.save(order);
        String paymentIntentId = null;
        String clientSecret = null;
        if (request.getPaymentMethod() == PaymentMethod.STRIPE_CARD) {
            try {
                PaymentIntent paymentIntent = stripeService.createPlatformCharge(order.getTotalAmount(), order.getOrderId());
                paymentIntentId = paymentIntent.getId();
                clientSecret = paymentIntent.getClientSecret();
                
                // Store the PaymentIntent ID on the order
                order.setStripePaymentIntentId(paymentIntentId);
                order = orderRepository.save(order);
                
                log.info("Created Stripe PaymentIntent {} for order {}", paymentIntentId, order.getOrderId());
            } catch (Exception e) {
                log.error("Failed to create Stripe payment for order {}: {}", order.getOrderId(), e.getMessage());
                throw new BusinessLogicException("Could not initialize payment. Please try again later.");
            }
        }

        List<OrderItem> orderItems = createOrderItems(itemDataList, order);
        order.setOrderItems(orderItems);
        
        // Save the order with its items to get proper IDs
        order = orderRepository.save(order);
        
        // Create transactions after order items have been saved and have IDs
        createTransactions(order.getOrderItems(), buyer, shippingAddress, paymentIntentId);
        markProductsAsPendingPayment(itemDataList);
        
        // Refresh the order from database to ensure all relationships are loaded
        order = orderRepository.findById(order.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Order not found after creation"));
        
        // Convert to response after everything is properly saved
        OrderDetailResponse response = convertToDetailResponse(order);
        if (clientSecret != null) {
            response.setMetadata(Map.of("clientSecret", clientSecret));
        }

        return response;
    }

    @Transactional
    public OrderDetailResponse confirmPayment(Long orderId, Long buyerId, String stripePaymentIntentId) {
        Order order = getOrderWithValidation(orderId, buyerId, true);

        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new BusinessLogicException("Order is not in pending status");
        }

        String cleanPaymentIntentId = cleanPaymentIntentId(stripePaymentIntentId);
        try {
            String paymentStatus = stripeService.getPaymentIntentStatus(cleanPaymentIntentId);

            boolean paymentSuccessful = stripeService.verifyPaymentForOrder(
                cleanPaymentIntentId, order.getTotalAmount(), orderId);
            if (!paymentSuccessful) {
                String errorMessage = switch (paymentStatus) {
                    case "requires_payment_method" -> 
                        "Payment not completed. Please complete the payment on the frontend using Stripe Elements with the client_secret.";
                    case "requires_confirmation" -> 
                        "Payment requires confirmation. Please confirm the payment on the frontend.";
                    case "requires_action" -> 
                        "Payment requires additional action (e.g., 3D Secure authentication).";
                    case "processing" -> 
                        "Payment is still processing. Please wait and try again.";
                    case "canceled" -> 
                        "Payment was canceled. Please create a new order.";
                    default -> 
                        "Payment verification failed. Current status: " + paymentStatus;
                };
                throw new BusinessLogicException(errorMessage);
            }
        } catch (BusinessLogicException e) {
            throw e;
        } catch (Exception e) {
            log.error("Payment verification failed for order {}: {}", orderId, e.getMessage());
            throw new BusinessLogicException("Payment verification failed: " + e.getMessage());
        }

        order.setStripePaymentIntentId(stripePaymentIntentId);
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());        order.getOrderItems().forEach(item -> {
            if (item.getStatus() == OrderItemStatus.PENDING) {
                item.setStatus(OrderItemStatus.PROCESSING);
                item.setEscrowStatus(EscrowStatus.HOLDING); // Now money is actually held in escrow
                
                Product product = item.getProduct();
                product.setStatus(ProductStatus.SOLD);
                product.setSoldAt(LocalDateTime.now());
                productRepository.save(product);
            }
        });

        // Update transaction escrow status when payment is confirmed
        for (OrderItem item : order.getOrderItems()) {
            Transaction transaction = getTransactionForOrderItem(item.getOrderItemId());
            if (transaction != null && transaction.getEscrowStatus() == EscrowStatus.CANCELLED) {
                transaction.setEscrowStatus(EscrowStatus.HOLDING);
                transactionRepository.save(transaction);
            }
        }

        order = orderRepository.save(order);
        return convertToDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse updateOrderItemStatus(Long orderId, Long itemId, OrderStatusUpdateRequest request, Long userId) {
        Order order = getOrderWithValidation(orderId, userId, false);
        OrderItem orderItem = findOrderItem(order, itemId);

        OrderItemStatus newStatus = parseOrderItemStatus(request.getStatus());
        boolean isBuyer = order.getBuyer().getUserId().equals(userId);
        boolean isSeller = orderItem.getSeller().getUserId().equals(userId);

        validateItemStatusUpdate(orderItem.getStatus(), newStatus, isBuyer, isSeller);

        orderItem.setStatus(newStatus);
        
        switch (newStatus) {
            case PENDING:
                // No special handling needed for pending status
                break;
            case PROCESSING:
                // No special handling needed for processing status
                break;
            case SHIPPED:
                handleItemShipped(orderItem, request);
                break;
            case DELIVERED:
                handleItemDelivered(orderItem);
                break;
            case CANCELLED:
                handleItemCancelled(orderItem);
                break;
            case REFUNDED:
                // Refund handling is done separately through admin endpoints
                break;
        }

        updateOverallOrderStatus(order);
        order = orderRepository.save(order);

        return convertToDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse cancelOrder(Long orderId, String reason, Long userId) {
        log.info("Attempting to cancel order: orderId={}, userId={}, reason={}", orderId, userId, reason);

        Order order = getOrderWithValidation(orderId, userId, false);

        boolean hasNonCancellableItems = order.getOrderItems().stream()
                .anyMatch(item -> !isItemCancellable(item.getStatus()));

        if (hasNonCancellableItems) {
            throw new BusinessLogicException("Cannot cancel order with shipped or delivered items.");
        }

        String paymentIntentId = order.getOrderItems().stream()
                .map(item -> getTransactionForOrderItem(item.getOrderItemId()))
                .map(Transaction::getStripePaymentIntentId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (paymentIntentId != null) {
            try {
                BigDecimal totalRefundAmount = order.getOrderItems().stream()
                        .filter(item -> isItemCancellable(item.getStatus()))
                        .map(OrderItem::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (totalRefundAmount.compareTo(BigDecimal.ZERO) > 0) {
                    log.info("Refunding {} for paymentIntentId {}", totalRefundAmount, paymentIntentId);
                    stripeService.refundPayment(paymentIntentId, totalRefundAmount);
                }

            } catch (Exception e) {
                log.error("Stripe refund failed for order {}: {}. Cancellation process aborted.", orderId, e.getMessage());
                throw new BusinessLogicException("Refund failed. Please try again or contact support. " + e.getMessage());
            }
        }

        order.getOrderItems().forEach(item -> {
            if (isItemCancellable(item.getStatus())) {
                item.setStatus(OrderItemStatus.CANCELLED);
                item.setEscrowStatus(EscrowStatus.REFUNDED);

                Transaction transaction = getTransactionForOrderItem(item.getOrderItemId());
                transaction.setStatus(TransactionStatus.CANCELLED);
                transactionRepository.save(transaction);

                Product product = item.getProduct();
                if (product.getStatus() == ProductStatus.SOLD || product.getStatus() == ProductStatus.PENDING_PAYMENT) {
                    product.setStatus(ProductStatus.ACTIVE);
                    product.setSoldAt(null);
                    productRepository.save(product);
                }
            }
        });

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        log.info("Order {} cancelled successfully.", orderId);
        return convertToDetailResponse(order);
    }    public PagedResponse<OrderListResponse> getUserOrders(Long userId, String status, String role, Pageable pageable) {
        Page<Order> orders;

        if ("seller".equalsIgnoreCase(role)) {
            orders = getSellerOrders(userId, status, pageable);
        } else {
            orders = getBuyerOrders(userId, status, pageable);
        }

        // Load product images separately to avoid MultipleBagFetchException
        loadProductImagesForOrders(orders.getContent());

        Page<OrderListResponse> orderResponses = orders.map(this::convertToListResponse);
        return PagedResponse.of(orderResponses);
    }

    public OrderDetailResponse getOrderById(Long orderId, Long userId) {
        Order order = getOrderWithValidation(orderId, userId, false);
        return convertToDetailResponse(order);
    }

    /**
     * Admin method to get all orders with comprehensive filtering
     * Supports filtering by status, buyer, seller, date range, and search
     */
    @Transactional(readOnly = true)
    public PagedResponse<OrderListResponse> getAllOrdersForAdmin(
            String status, Long buyerId, Long sellerId, String search,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
          // Use specification pattern with proper JOIN FETCH for relationships
        Page<Order> orders = orderRepository.findAll((root, query, criteriaBuilder) -> {
            // Add JOIN FETCH for required relationships
            root.fetch("buyer", JoinType.LEFT);
            root.fetch("orderItems", JoinType.LEFT).fetch("product", JoinType.LEFT);
            root.fetch("orderItems", JoinType.LEFT).fetch("seller", JoinType.LEFT);
            root.fetch("shippingAddress", JoinType.LEFT);
            
            // Make the query DISTINCT to avoid duplicates from JOIN FETCH
            query.distinct(true);
            
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            // Filter by status if provided
            if (status != null && !status.trim().isEmpty()) {
                try {
                    OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("status"), orderStatus));
                } catch (IllegalArgumentException e) {
                    // Invalid status - return empty result
                    predicates.add(criteriaBuilder.disjunction());
                }
            }
            
            // Filter by buyer ID
            if (buyerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("buyer").get("userId"), buyerId));
            }
            
            // Filter by seller ID (requires join to order items)
            if (sellerId != null) {
                var orderItemsJoin = root.join("orderItems");
                predicates.add(criteriaBuilder.equal(orderItemsJoin.get("seller").get("userId"), sellerId));
            }
            
            // Filter by date range
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            
            // Search functionality (search in buyer username or order ID)
            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.toLowerCase() + "%";
                jakarta.persistence.criteria.Predicate searchPredicate = criteriaBuilder.or(
                    criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("buyer").get("username")), 
                        searchPattern
                    ),
                    criteriaBuilder.like(
                        criteriaBuilder.toString(root.get("orderId")), 
                        searchPattern
                    )
                );
                predicates.add(searchPredicate);
            }
            
            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));        }, pageable);
        
        // Load product images separately to avoid MultipleBagFetchException
        loadProductImagesForOrders(orders.getContent());
        
        Page<OrderListResponse> orderResponses = orders.map(this::convertToListResponse);
        return PagedResponse.of(orderResponses);
    }

    /**
     * Admin method to force update order status with override capabilities
     * Allows admins to set any status regardless of current state
     */
    @Transactional
    public OrderDetailResponse forceUpdateOrderStatus(Long orderId, String newStatus, String adminNotes, Long adminId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));
        
        OrderStatus orderStatus;
        try {
            orderStatus = OrderStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessLogicException("Invalid order status: " + newStatus);
        }
        
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(orderStatus);

        LocalDateTime now = LocalDateTime.now();
        switch (orderStatus) {
            case PENDING -> {
                // No specific timestamp needed for pending
            }
            case PAID -> order.setPaidAt(now);
            case PROCESSING -> {
                // No specific timestamp needed for processing
            }
            case SHIPPED -> order.setShippedAt(now);
            case DELIVERED -> order.setDeliveredAt(now);
            case CANCELLED -> {
                // No specific timestamp needed for cancelled (could add cancelledAt if field exists)
            }
            case REFUNDED -> {
                // No specific timestamp needed for refunded (could add refundedAt if field exists)
            }
        }
        
        // Log admin action
        log.info("Admin {} force updated order {} status from {} to {}. Notes: {}", 
                adminId, orderId, oldStatus, orderStatus, adminNotes);
        
        order = orderRepository.save(order);
        return convertToDetailResponse(order);
    }

    /**
     * Admin method to process refund for any order
     * Handles both Stripe refunds and manual refund marking
     */
    @Transactional
    public OrderDetailResponse processAdminRefund(Long orderId, BigDecimal refundAmount, String reason, Long adminId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));
        
        // Validate refund amount before processing
        validateRefundAmount(order, refundAmount);
        
        // Use stored PaymentIntent ID or get from transaction
        String paymentIntentId = order.getStripePaymentIntentId();
        if (paymentIntentId == null) {
            paymentIntentId = order.getOrderItems().stream()
                    .map(item -> getTransactionForOrderItem(item.getOrderItemId()))
                    .map(Transaction::getStripePaymentIntentId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        
        // Process Stripe refund if payment intent exists
        if (paymentIntentId != null) {
            try {
                log.info("Admin {} processing refund for order {} amount {} reason: {}", 
                        adminId, orderId, refundAmount, reason);
                stripeService.refundPayment(paymentIntentId, refundAmount);
            } catch (Exception e) {
                log.error("Admin refund failed for order {}: {}", orderId, e.getMessage());
                throw new BusinessLogicException("Stripe refund failed: " + e.getMessage());
            }
        }
        
        // Update order status only if full refund
        BigDecimal totalOrderAmount = order.getTotalAmount();
        if (refundAmount.compareTo(totalOrderAmount) >= 0) {
            order.setStatus(OrderStatus.REFUNDED);
            
            // Update all order items for full refund
            order.getOrderItems().forEach(item -> {
                if (item.getStatus() != OrderItemStatus.DELIVERED) {
                    item.setStatus(OrderItemStatus.REFUNDED);
                    
                    // Revert product status if it was sold
                    Product product = item.getProduct();
                    if (ProductStatus.SOLD.equals(product.getStatus())) {
                        product.setStatus(ProductStatus.ACTIVE);
                        product.setSoldAt(null);
                        productRepository.save(product);
                    }
                }
            });
        } else {
            // Partial refund - keep order status as is, but mark individual items as needed
            log.info("Partial refund processed for order {}. Amount: {}", orderId, refundAmount);
        }
        
        log.info("Admin {} successfully processed refund for order {}", adminId, orderId);
        
        order = orderRepository.save(order);
        return convertToDetailResponse(order);
    }

    /**
     * Get comprehensive system-wide order statistics for admin dashboard
     */
    @Transactional(readOnly = true)
    public Object getSystemOrderStats() {
        // Get total counts by status
        Map<OrderStatus, Long> statusCounts = Arrays.stream(OrderStatus.values())
                .collect(Collectors.toMap(
                    status -> status,
                        orderRepository::countByStatus,
                    (existing, replacement) -> existing,
                    LinkedHashMap::new
                ));
        
        // Get today's orders
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        long todayOrders = orderRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        
        // Calculate total revenue (completed orders)
        BigDecimal totalRevenue = orderRepository.findByStatus(OrderStatus.DELIVERED)
                .stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate platform fees collected
        BigDecimal totalPlatformFees = orderItemRepository.findByStatus(OrderItemStatus.DELIVERED)
                .stream()
                .map(OrderItem::getPlatformFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return Map.of(
                "totalOrders", orderRepository.count(),
                "todayOrders", todayOrders,
                "totalRevenue", totalRevenue,
                "platformFeesCollected", totalPlatformFees,
                "statusBreakdown", statusCounts,
                "avgOrderValue", statusCounts.get(OrderStatus.DELIVERED) > 0 ? 
                    totalRevenue.divide(BigDecimal.valueOf(statusCounts.get(OrderStatus.DELIVERED)), 2, RoundingMode.HALF_UP) : 
                    BigDecimal.ZERO
        );
    }

    /**
     * Utility method to parse date strings for admin filtering
     */
    public LocalDateTime parseDateTime(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }
        try {
            // Support multiple date formats
            if (dateString.length() == 10) {
                // Date only format (YYYY-MM-DD) - add time
                return LocalDateTime.parse(dateString + "T00:00:00");
            }
            return LocalDateTime.parse(dateString);
        } catch (Exception e) {
            throw new BusinessLogicException("Invalid date format: " + dateString + ". Use ISO format (YYYY-MM-DDTHH:mm:ss)");
        }
    }

    // Webhook handlers
    @Transactional
    public void handleSuccessfulPayment(String paymentIntentId) {
        log.info("Handling successful payment for PaymentIntent: {}", paymentIntentId);

        Transaction transaction = transactionRepository.findFirstByStripePaymentIntentId(paymentIntentId)
                .orElse(null);

        if (transaction == null) {
            log.warn("Received payment_intent.succeeded webhook for a transaction not found in DB. PI: {}", paymentIntentId);
            return;
        }

        Order order = transaction.getOrderItem().getOrder();
        if (!order.getStatus().equals(OrderStatus.PENDING)) {
            log.info("Order {} has already been processed. Skipping update.", order.getOrderId());
            return;
        }

        log.info("Updating order {} to PAID.", order.getOrderId());
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());

        order.getOrderItems().forEach(item -> {
            item.setStatus(OrderItemStatus.PROCESSING);
            Product product = item.getProduct();
            product.setStatus(ProductStatus.SOLD);
            product.setSoldAt(LocalDateTime.now());
            productRepository.save(product);
        });

        orderRepository.save(order);
        log.info("Order {} successfully updated after payment.", order.getOrderId());
    }

    @Transactional
    public void handleChargeDisputeCreated(Dispute dispute) {
        String paymentIntentId = dispute.getPaymentIntent();
        log.warn("Dispute created for PaymentIntent {}. Reason: {}", paymentIntentId, dispute.getReason());

        transactionRepository.findFirstByStripePaymentIntentId(paymentIntentId).ifPresent(transaction -> {
            transaction.setDisputeStatus(DisputeStatus.OPEN);
            transaction.setDisputeReason(dispute.getReason());
            transactionRepository.save(transaction);
            log.info("Transaction {} marked as DISPUTED.", transaction.getTransactionId());
        });
    }

    @Transactional
    public void handleTransferFailed(Transfer transfer) {
        // Get transactionId from metadata that we saved when creating transfer
        String transactionId = transfer.getMetadata().get("transaction_id");
        log.error("Transfer {} failed for transactionId {}.", transfer.getId(), transactionId);

        if (transactionId != null) {
            transactionRepository.findById(Long.parseLong(transactionId)).ifPresent(transaction -> {
                transaction.setEscrowStatus(EscrowStatus.TRANSFER_FAILED);
                transactionRepository.save(transaction);
                log.info("Transaction {} marked as TRANSFER_FAILED.", transaction.getTransactionId());
            });
        }
    }

    @Transactional
    public void handleTransferCreated(Transfer transfer) {
        String transactionId = transfer.getMetadata().get("transaction_id");
        log.info("Transfer {} created for transactionId {}.", transfer.getId(), transactionId);

        if (transactionId != null) {
            transactionRepository.findById(Long.parseLong(transactionId)).ifPresent(transaction -> {
                transaction.setStripeTransferId(transfer.getId()); // Save transfer ID
                transactionRepository.save(transaction);
            });
        }
    }

    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void reconcilePendingOrders() {
        log.info("Starting pending orders reconciliation job...");

        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<Order> potentiallyStuckOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, oneHourAgo);

        if (potentiallyStuckOrders.isEmpty()) {
            log.info("Reconciliation job finished. No stuck orders found.");
            return;
        }

        log.warn("Found {} potentially stuck orders. Starting verification with Stripe.", potentiallyStuckOrders.size());

        for (Order order : potentiallyStuckOrders) {
            if (order.getPaymentMethod() != PaymentMethod.STRIPE_CARD) {
                continue;
            }

            String paymentIntentId = order.getOrderItems().stream()
                    .map(item -> getTransactionForOrderItem(item.getOrderItemId()).getStripePaymentIntentId())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);            if (paymentIntentId != null) {
                try {
                    if (stripeService.verifyPayment(paymentIntentId)) {
                        log.warn("Reconciliation: Order {} was paid on Stripe but stuck in PENDING. Updating status now.", order.getOrderId());
                        handleSuccessfulPayment(paymentIntentId);
                    } else {
                        log.warn("Reconciliation: Order {} has failed payment on Stripe. Expiring order now.", order.getOrderId());
                        expireOrder(order);
                    }
                } catch (Exception e) {
                    log.error("Error during reconciliation check for order {}: {}", order.getOrderId(), e.getMessage());
                }
            }
        }
        log.info("Reconciliation job finished.");
    }

    /**
     * Scheduled job to clean up expired unpaid orders
     * Runs every 30 minutes to check for orders that have been pending payment for more than 30 minutes
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    @Transactional
    public void cleanupExpiredPendingOrders() {
        LocalDateTime expirationTime = LocalDateTime.now().minusMinutes(30);
        
        List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(
            OrderStatus.PENDING, expirationTime);
        
        if (!expiredOrders.isEmpty()) {
            log.info("Found {} expired pending orders to clean up", expiredOrders.size());
            
            for (Order order : expiredOrders) {
                try {
                    expireOrder(order);
                    log.info("Successfully expired order {}", order.getOrderId());
                } catch (Exception e) {
                    log.error("Failed to expire order {}: {}", order.getOrderId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Scheduled task to clean up expired orders (runs every 15 minutes)
     * Orders that are PENDING for more than 30 minutes will be expired
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    @Transactional
    public void cleanupExpiredOrders() {
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        
        List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(
            OrderStatus.PENDING, thirtyMinutesAgo);
        
        if (!expiredOrders.isEmpty()) {
            log.info("Found {} expired orders to clean up", expiredOrders.size());
            
            for (Order order : expiredOrders) {
                try {
                    expireOrder(order);
                    log.info("Successfully expired order {}", order.getOrderId());
                } catch (Exception e) {
                    log.error("Failed to expire order {}: {}", order.getOrderId(), e.getMessage());
                }
            }
        }
    }    /**
     * Expire a single order and restore product availability
     */    @Transactional
    public void expireOrder(Order order) {
        // Mark order as expired
        order.setStatus(OrderStatus.EXPIRED);
        
        // Restore product availability
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            if (product.getStatus() == ProductStatus.PENDING_PAYMENT) {
                product.setStatus(ProductStatus.ACTIVE);
                product.setUpdatedAt(LocalDateTime.now());
                productRepository.save(product);
                
                log.info("Restored product {} availability after order {} expiration", 
                    product.getProductId(), order.getOrderId());
            }
              // Mark order item as cancelled
            item.setStatus(OrderItemStatus.CANCELLED);
            item.setEscrowStatus(EscrowStatus.CANCELLED); // No money involved yet
        }        // Cancel all transactions (if they exist)
        for (OrderItem item : order.getOrderItems()) {
            Optional<Transaction> transactionOpt = getTransactionForOrderItemSafely(item.getOrderItemId());
            if (transactionOpt.isPresent()) {
                Transaction transaction = transactionOpt.get();
                transaction.setStatus(TransactionStatus.CANCELLED);
                transactionRepository.save(transaction);
                log.debug("Cancelled transaction for order item {}", item.getOrderItemId());
            } else {
                // Transaction doesn't exist for this order item (normal for unpaid orders)
                log.debug("No transaction found for order item {} - this is normal for unpaid orders", item.getOrderItemId());
            }
        }
        
        orderRepository.save(order);
        log.info("Order {} expired and cleaned up successfully", order.getOrderId());
    }

    /**
     * Find expired orders for manual inspection
     */
    @Transactional(readOnly = true)
    public List<Order> findExpiredOrders(LocalDateTime cutoffTime) {
        return orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoffTime);
    }

    /**
     * Manual cleanup method for admin use
     */
    @Transactional
    public int cleanupExpiredOrdersManually() {
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        
        List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(
            OrderStatus.PENDING, thirtyMinutesAgo);
        
        int cleanedCount = 0;
        for (Order order : expiredOrders) {
            try {
                expireOrder(order);
                cleanedCount++;
                log.info("Manually expired order {}", order.getOrderId());
            } catch (Exception e) {
                log.error("Failed to manually expire order {}: {}", order.getOrderId(), e.getMessage());
            }
        }
        
        log.info("Manual cleanup completed: {} orders expired", cleanedCount);
        return cleanedCount;
    }

    // Private helper methods

    private List<OrderItemData> validateAndProcessItems(List<OrderCreateRequest.OrderItemRequest> itemRequests, Long buyerId) {
        if (itemRequests.isEmpty()) {
            throw new BusinessLogicException("Order must contain at least one item");
        }

        List<OrderItemData> itemDataList = new ArrayList<>();
        for (OrderCreateRequest.OrderItemRequest itemRequest : itemRequests) {
            OrderItemData itemData = validateAndProcessItem(itemRequest, buyerId);
            itemDataList.add(itemData);
        }

        return itemDataList;
    }

    private OrderItemData validateAndProcessItem(OrderCreateRequest.OrderItemRequest itemRequest, Long buyerId) {
        Product product = productRepository.findById(itemRequest.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.getProductId()));

        // Validate product status
        if (!ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new BusinessLogicException("Product '" + product.getTitle() + "' is not available for purchase");
        }

        // Validate not buying own product
        if (product.getSeller().getUserId().equals(buyerId)) {
            throw new BusinessLogicException("Cannot purchase your own product: " + product.getTitle());
        }

        // Validate seller has Stripe account for non-COD payments
        if (product.getSeller().getStripeAccountId() == null) {
            throw new BusinessLogicException("Seller must complete payment setup before selling products");
        }

        // Handle offer or regular price
        BigDecimal itemPrice;
        Offer offer = null;

        if (itemRequest.getOfferId() != null) {
            offer = validateOffer(itemRequest.getOfferId(), product.getProductId(), buyerId);
            itemPrice = offer.getAmount();
        } else {
            itemPrice = product.getPrice();
        }

        // Calculate fees
        BigDecimal platformFee = feeTierService.calculatePlatformFee(itemPrice, product.getSeller());
        BigDecimal feePercentage = feeTierService.calculateFeePercentage(itemPrice, product.getSeller());

        return OrderItemData.builder()
                .product(product)
                .offer(offer)
                .itemPrice(itemPrice)
                .platformFee(platformFee)
                .feePercentage(feePercentage)
                .notes(itemRequest.getNotes())
                .build();
    }

    private Offer validateOffer(Long offerId, Long productId, Long buyerId) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found: " + offerId));

        if (!offer.getProduct().getProductId().equals(productId)) {
            throw new BusinessLogicException("Offer does not match product");
        }

        if (!OfferStatus.ACCEPTED.equals(offer.getStatus())) {
            throw new BusinessLogicException("Offer is not accepted");
        }

        if (!offer.getBuyer().getUserId().equals(buyerId)) {
            throw new UnauthorizedException("Offer does not belong to buyer");
        }

        return offer;
    }

    private List<OrderItem> createOrderItems(List<OrderItemData> itemDataList, Order order) {
        return itemDataList.stream()
                .map(data -> OrderItem.builder()                        .order(order)
                        .product(data.getProduct())
                        .seller(data.getProduct().getSeller())
                        .price(data.getItemPrice())
                        .platformFee(data.getPlatformFee())
                        .feePercentage(data.getFeePercentage())
                        .status(OrderItemStatus.PENDING)
                        .escrowStatus(EscrowStatus.CANCELLED) // No money yet, will change to HOLDING after payment
                        .build())
                .collect(Collectors.toList());
    }

    private void createTransactions(List<OrderItem> orderItems, User buyer, UserAddress shippingAddress, String paymentIntentId) {
        for (OrderItem orderItem : orderItems) {
            Transaction transaction = Transaction.builder()
                    .orderItem(orderItem)
                    .seller(orderItem.getSeller())
                    .buyer(buyer)
                    .amount(orderItem.getPrice())
                    .platformFee(orderItem.getPlatformFee())
                    .feePercentage(orderItem.getFeePercentage())
                    .shippingAddress(shippingAddress)
                    .status(TransactionStatus.PENDING)
                    .escrowStatus(EscrowStatus.CANCELLED) // No money yet, will change to HOLDING after payment
                    .stripePaymentIntentId(paymentIntentId)
                    .build();
            transactionRepository.save(transaction);
        }
    }

    private void markProductsAsPendingPayment(List<OrderItemData> itemDataList) {
        itemDataList.forEach(data -> {
            Product product = data.getProduct();
            product.setStatus(ProductStatus.PENDING_PAYMENT);
            productRepository.save(product);
        });
    }

    private void handleItemShipped(OrderItem orderItem, OrderStatusUpdateRequest request) {
        if (request.getTrackingNumber() == null || request.getTrackingNumber().isBlank()) {
            throw new BusinessLogicException("Tracking number is required to mark item as shipped");
        }

        Transaction transaction = getTransactionForOrderItem(orderItem.getOrderItemId());
        transaction.setShippedAt(LocalDateTime.now());
        transaction.setTrackingNumber(request.getTrackingNumber());
        transaction.setTrackingUrl(request.getTrackingUrl());
        transaction.setStatus(TransactionStatus.SHIPPED);
        transactionRepository.save(transaction);
    }

    private void handleItemDelivered(OrderItem orderItem) {
        Transaction transaction = getTransactionForOrderItem(orderItem.getOrderItemId());
        transaction.setDeliveredAt(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.DELIVERED);
        orderItem.setEscrowStatus(EscrowStatus.RELEASED);

        transactionRepository.save(transaction);
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void processScheduledTransfers() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        List<Transaction> readyTransactions = transactionRepository
                .findByStatusAndDeliveredAtBeforeAndEscrowStatus(
                        TransactionStatus.DELIVERED,
                        sevenDaysAgo,
                        EscrowStatus.RELEASED
                );

        if (!readyTransactions.isEmpty()) {
            log.info("Found {} transactions ready for seller payout.", readyTransactions.size());
        }

        for (Transaction transaction : readyTransactions) {
            try {
                OrderItem orderItem = transaction.getOrderItem();
                log.info("Attempting to release payment for transaction {}.", transaction.getTransactionId());
                stripeService.releasePaymentToSeller(orderItem, transaction);

                transaction.setEscrowStatus(EscrowStatus.TRANSFERRED);
                transactionRepository.save(transaction);

                log.info("Successfully released payment for transaction {}", transaction.getTransactionId());
            } catch (Exception e) {
                log.error("Failed to release payment for transaction {}: {}. Marking as TRANSFER_FAILED.",
                        transaction.getTransactionId(), e.getMessage());

                transaction.setEscrowStatus(EscrowStatus.TRANSFER_FAILED);
                transactionRepository.save(transaction);
            }
        }
    }

    private void handleItemCancelled(OrderItem orderItem) {
        orderItem.setEscrowStatus(EscrowStatus.REFUNDED);

        // Restore product status
        Product product = orderItem.getProduct();
        if (product.getStatus() == ProductStatus.SOLD || product.getStatus() == ProductStatus.PENDING_PAYMENT) {
            product.setStatus(ProductStatus.ACTIVE);
            product.setSoldAt(null);
            productRepository.save(product);
        }

        // Update transaction
        Transaction transaction = getTransactionForOrderItem(orderItem.getOrderItemId());
        transaction.setStatus(TransactionStatus.CANCELLED);
        transactionRepository.save(transaction);
    }

    private Transaction getTransactionForOrderItem(Long orderItemId) {
        return transactionRepository.findByOrderItemOrderItemId(orderItemId)
                .orElseThrow(() -> new IllegalStateException("Transaction not found for order item: " + orderItemId));
    }

    /**
     * Safely get transaction for order item without throwing exception if not found
     */
    private Optional<Transaction> getTransactionForOrderItemSafely(Long orderItemId) {
        return transactionRepository.findByOrderItemOrderItemId(orderItemId);
    }

    private Page<Order> getBuyerOrders(Long userId, String status, Pageable pageable) {
        if (status != null && !status.trim().isEmpty()) {
            try {
                OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
                return orderRepository.findByBuyerUserIdAndStatusOrderByCreatedAtDesc(userId, orderStatus, pageable);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid OrderStatus: {}. Returning all orders", status);
            }
        }
        return orderRepository.findByBuyerUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    private Page<Order> getSellerOrders(Long userId, String status, Pageable pageable) {
        Pageable adjustedPageable = adjustPageableForSellerOrders(pageable);
        
        Page<OrderItem> sellerItems;
        if (status != null && !status.trim().isEmpty()) {
            try {
                OrderItemStatus itemStatus = OrderItemStatus.valueOf(status.toUpperCase());
                sellerItems = orderItemRepository.findBySellerUserIdAndStatusOrderByOrderCreatedAtDesc(userId, itemStatus, adjustedPageable);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid OrderItemStatus: {}. Returning all items", status);
                sellerItems = orderItemRepository.findBySellerUserIdOrderByOrderCreatedAtDesc(userId, adjustedPageable);
            }
        } else {
            sellerItems = orderItemRepository.findBySellerUserIdOrderByOrderCreatedAtDesc(userId, adjustedPageable);
        }

        List<Order> uniqueOrders = sellerItems.getContent().stream()
                .map(OrderItem::getOrder)
                .distinct()
                .collect(Collectors.toList());

        return new PageImpl<>(uniqueOrders, pageable, sellerItems.getTotalElements());
    }

    private OrderItem findOrderItem(Order order, Long itemId) {
        return order.getOrderItems().stream()
                .filter(item -> item.getOrderItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found: " + itemId));
    }

    private OrderItemStatus parseOrderItemStatus(String status) {
        try {
            return OrderItemStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessLogicException("Invalid order item status: " + status);
        }
    }

    private void validateItemStatusUpdate(OrderItemStatus currentStatus, OrderItemStatus newStatus, boolean isBuyer, boolean isSeller) {
        switch (newStatus) {
            case SHIPPED:
                if (!isSeller) {
                    throw new UnauthorizedException("Only the seller can mark an item as shipped");
                }
                if (!OrderItemStatus.PROCESSING.equals(currentStatus)) {
                    throw new BusinessLogicException("Item can only be shipped from processing status");
                }
                break;
            case DELIVERED:
                if (!isBuyer) {
                    throw new UnauthorizedException("Only the buyer can confirm delivery");
                }
                if (!OrderItemStatus.SHIPPED.equals(currentStatus)) {
                    throw new BusinessLogicException("Item can only be delivered from shipped status");
                }
                break;
            case CANCELLED:
                if (!isItemCancellable(currentStatus)) {
                    throw new BusinessLogicException("Item cannot be cancelled in " + currentStatus + " status");
                }
                if (!isBuyer && !isSeller) {
                    throw new UnauthorizedException("User not authorized to cancel this item");
                }
                break;
            default:
                throw new BusinessLogicException("Status " + newStatus + " cannot be set directly");
        }
    }

    private boolean isItemCancellable(OrderItemStatus status) {
        return status == OrderItemStatus.PENDING || status == OrderItemStatus.PROCESSING;
    }

    private void updateOverallOrderStatus(Order order) {
        List<OrderItemStatus> itemStatuses = order.getOrderItems().stream()
                .map(OrderItem::getStatus)
                .toList();

        if (itemStatuses.isEmpty()) {
            order.setStatus(OrderStatus.CANCELLED);
            return;
        }

        long totalItems = itemStatuses.size();
        long deliveredItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.DELIVERED).count();
        long cancelledItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.CANCELLED).count();
        long shippedItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.SHIPPED).count();
        long processingItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.PROCESSING).count();

        if (cancelledItems == totalItems) {
            order.setStatus(OrderStatus.CANCELLED);
        } else if (deliveredItems > 0 && deliveredItems + cancelledItems == totalItems) {
            order.setStatus(OrderStatus.DELIVERED);
        } else if (shippedItems > 0) {
            order.setStatus(OrderStatus.SHIPPED);
        } else if (processingItems > 0) {
            order.setStatus(OrderStatus.PROCESSING);
        }
    }

    private Order getOrderWithValidation(Long orderId, Long userId, boolean buyerOnly) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        boolean isBuyer = order.getBuyer().getUserId().equals(userId);
        boolean isSeller = order.getOrderItems().stream()
                .anyMatch(item -> item.getSeller().getUserId().equals(userId));

        if (buyerOnly) {
            if (!isBuyer) {
                throw new UnauthorizedException("User is not the buyer of this order");
            }
        } else {
            if (!isBuyer && !isSeller) {
                throw new UnauthorizedException("User not authorized to access this order");
            }
        }

        return order;
    }

    /**
     * Validate partial refund amount against order limits
     */
    private void validateRefundAmount(Order order, BigDecimal refundAmount) {
        BigDecimal maxRefundableAmount = order.getOrderItems().stream()
                .filter(item -> item.getStatus() != OrderItemStatus.DELIVERED) // Can't refund delivered items
                .map(OrderItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (refundAmount.compareTo(maxRefundableAmount) > 0) {
            throw new BusinessLogicException(String.format(
                "Refund amount %s exceeds maximum refundable amount %s for order %d", 
                refundAmount, maxRefundableAmount, order.getOrderId()));
        }
        
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessLogicException("Refund amount must be greater than zero");
        }
    }

    /**
     * Calculate total amount already refunded for an order
     */
    private BigDecimal getTotalRefundedAmount(Order order) {
        // This would need to track refunds in a separate table or in transaction records
        // For now, return zero - this should be implemented based on your refund tracking strategy
        return BigDecimal.ZERO;
    }    // Response conversion methods
    private OrderDetailResponse convertToDetailResponse(Order order) {
        Map<String, List<OrderDetailResponse.OrderItemDetail>> itemsBySeller = order.getOrderItems().stream()
                .map(this::convertToOrderItemDetail)
                .collect(Collectors.groupingBy(item -> item.getSeller().getUserId().toString()));

        List<OrderDetailResponse.OrderItemDetail> itemDetails = order.getOrderItems().stream()
                .map(this::convertToOrderItemDetail)
                .collect(Collectors.toList());

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
                .stripePaymentIntentId(order.getStripePaymentIntentId())                .orderItems(itemDetails)
                .shippingAddress(convertToShippingAddressInfo(order.getShippingAddress()))
                .itemsBySeller(itemsBySeller)
                .build();
    }

    private OrderListResponse convertToListResponse(Order order) {
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
    }private OrderDetailResponse.OrderItemDetail convertToOrderItemDetail(OrderItem orderItem) {
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
    }    private OrderListResponse.OrderItemSummary convertToOrderItemSummary(OrderItem orderItem) {
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

    private OrderDetailResponse.ProductInfo convertToProductInfo(Product product) {
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

    private OrderDetailResponse.SellerInfo convertToSellerInfo(User seller) {
        return OrderDetailResponse.SellerInfo.builder()
            .userId(seller.getUserId())
            .username(seller.getUsername())
            .firstName(seller.getFirstName())
            .lastName(seller.getLastName())
            .isLegitProfile(seller.getIsLegitProfile())
            .sellerRating(seller.getSellerRating())
            .sellerReviewsCount(seller.getSellerReviewsCount())
            .build();
    }    private OrderDetailResponse.TransactionInfo convertToTransactionInfo(OrderItem orderItem) {
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

    private OrderDetailResponse.ShippingAddressInfo convertToShippingAddressInfo(UserAddress address) {
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

    private BigDecimal calculateTotalPlatformFee(Order order) {
        return order.getOrderItems().stream()
            .map(OrderItem::getPlatformFee)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Data class for order processing
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemData {
        private Product product;
        private Offer offer;
        private BigDecimal itemPrice;
        private BigDecimal platformFee;
        private BigDecimal feePercentage;
        private String notes;
    }

    /**
     * Utility method to clean PaymentIntent ID by removing surrounding quotes and whitespace
     * This handles cases where the frontend sends the ID with JSON string quotes
     */    private String cleanPaymentIntentId(String paymentIntentId) {
        if (paymentIntentId == null) {
            return null;
        }
        
        // Remove surrounding quotes and trim whitespace
        String cleanedId = paymentIntentId.trim();
        
        // Remove surrounding double quotes if present
        if (cleanedId.startsWith("\"") && cleanedId.endsWith("\"")) {
            cleanedId = cleanedId.substring(1, cleanedId.length() - 1);
        }
        
        // Remove escaped quotes if present
        cleanedId = cleanedId.replace("\\\"", "");
        
        return cleanedId.trim();
    }

    /**
     * Check payment status for debugging
     */
    @Transactional(readOnly = true)
    public String checkPaymentStatus(String paymentIntentId) {
        try {
            return stripeService.getPaymentIntentStatus(paymentIntentId);
        } catch (Exception e) {
            log.error("Failed to check payment status for PI {}: {}", paymentIntentId, e.getMessage());
            throw new BusinessLogicException("Failed to check payment status: " + e.getMessage());
        }
    }

    /**
     * Simulate payment completion for testing purposes
     * WARNING: This bypasses actual Stripe verification - for development/testing only
     */
    @Transactional
    public OrderDetailResponse simulatePaymentCompletion(Long orderId, Long buyerId, String stripePaymentIntentId) {
        log.warn("SIMULATING payment completion for order {} - THIS SHOULD NOT BE USED IN PRODUCTION", orderId);
        
        Order order = getOrderWithValidation(orderId, buyerId, true);

        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new BusinessLogicException("Order is not in pending status");
        }

        // Simulate payment completion without Stripe verification
        order.setStripePaymentIntentId(stripePaymentIntentId);
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        
        order.getOrderItems().forEach(item -> {
            if (item.getStatus() == OrderItemStatus.PENDING) {
                item.setStatus(OrderItemStatus.PROCESSING);
                item.setEscrowStatus(EscrowStatus.HOLDING);
                
                Product product = item.getProduct();
                product.setStatus(ProductStatus.SOLD);
                product.setSoldAt(LocalDateTime.now());
                productRepository.save(product);
            }
        });

        // Update transaction escrow status
        for (OrderItem item : order.getOrderItems()) {
            Transaction transaction = getTransactionForOrderItem(item.getOrderItemId());
            if (transaction != null && transaction.getEscrowStatus() == EscrowStatus.CANCELLED) {
                transaction.setEscrowStatus(EscrowStatus.HOLDING);
                transactionRepository.save(transaction);
            }
        }

        order = orderRepository.save(order);
        return convertToDetailResponse(order);
    }

    /**
     * Adjust Pageable for seller orders to avoid sorting by non-existent OrderItem fields
     * Since seller orders query OrderItem but we want to sort by Order fields, we need to remove
     * any sort fields that don't exist on OrderItem
     */
    private Pageable adjustPageableForSellerOrders(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        
        // For seller orders, we only want to sort by the @Query ORDER BY clause
        // Remove any additional sorting from Pageable to avoid conflicts
        return PageRequest.of(
            pageable.getPageNumber(), 
            pageable.getPageSize()
            // No additional sorting - rely on @Query ORDER BY clause
        );
    }

    private String calculateOverallEscrowStatus(Order order) {
        if (order.getOrderItems().isEmpty()) {
            return EscrowStatus.CANCELLED.name();
        }
        
        // Get all unique escrow statuses from order items
        Set<EscrowStatus> escrowStatuses = order.getOrderItems().stream()
            .map(OrderItem::getEscrowStatus)
            .collect(Collectors.toSet());
        
        // If all items have the same escrow status, return that status
        if (escrowStatuses.size() == 1) {
            return escrowStatuses.iterator().next().name();
        }
        
        // Multiple different statuses - determine the overall status based on priority
        if (escrowStatuses.contains(EscrowStatus.HOLDING)) {
            return EscrowStatus.HOLDING.name(); // If any item is holding money, overall is holding
        } else if (escrowStatuses.contains(EscrowStatus.RELEASED)) {
            return EscrowStatus.RELEASED.name(); // If any item is released, overall is released
        } else if (escrowStatuses.contains(EscrowStatus.TRANSFERRED)) {
            return EscrowStatus.TRANSFERRED.name(); // If any item is transferred, overall is transferred
        } else if (escrowStatuses.contains(EscrowStatus.REFUNDED)) {
            return EscrowStatus.REFUNDED.name(); // If any item is refunded, overall is refunded
        } else {
            return EscrowStatus.CANCELLED.name(); // Default to cancelled
        }
    }
    
    /**
     * Load product images separately to avoid MultipleBagFetchException
     * This method takes orders that have already been loaded and loads the images for all products
     */
    private void loadProductImagesForOrders(List<Order> orders) {
        if (orders.isEmpty()) {
            return;
        }
        
        // Collect all unique product IDs from the orders
        Set<Long> productIds = orders.stream()
            .flatMap(order -> order.getOrderItems().stream())
            .map(orderItem -> orderItem.getProduct().getProductId())
            .collect(Collectors.toSet());
        
        if (productIds.isEmpty()) {
            return;
        }
        
        // Load all products with their images in a single query
        List<Product> productsWithImages = productRepository.findByProductIdInWithImages(new ArrayList<>(productIds));
        
        // Create a map for quick lookup
        Map<Long, Product> productImageMap = productsWithImages.stream()
            .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        
        // Update the products in the orders with the image data
        orders.forEach(order -> 
            order.getOrderItems().forEach(orderItem -> {
                Product productWithImages = productImageMap.get(orderItem.getProduct().getProductId());
                if (productWithImages != null) {
                    // Copy the images from the fetched product to the existing product
                    orderItem.getProduct().setImages(productWithImages.getImages());
                }
            })
        );
    }

    /**
     * Get detailed transaction analytics for admin dashboard
     */
    @Transactional(readOnly = true)
    public Object getTransactionAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime defaultStart = startDate != null ? startDate : LocalDateTime.now().minusMonths(3);
        LocalDateTime defaultEnd = endDate != null ? endDate : LocalDateTime.now();
        
        // Get all transactions within date range
        List<Transaction> transactions = transactionRepository.findByCreatedAtBetween(defaultStart, defaultEnd);
        
        // Calculate analytics
        long totalTransactions = transactions.size();
        BigDecimal totalVolume = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Group by escrow status
        Map<EscrowStatus, Long> escrowStatusCounts = transactions.stream()
                .collect(Collectors.groupingBy(
                    Transaction::getEscrowStatus,
                    Collectors.counting()
                ));
        
        // Group by transaction status
        Map<TransactionStatus, Long> transactionStatusCounts = transactions.stream()
                .collect(Collectors.groupingBy(
                    Transaction::getStatus,
                    Collectors.counting()
                ));
        
        // Calculate dispute rate
        long disputedTransactions = transactions.stream()
                .mapToLong(t -> t.getDisputeStatus() == DisputeStatus.OPEN ? 1 : 0)
                .sum();
        
        double disputeRate = totalTransactions > 0 ? (double) disputedTransactions / totalTransactions * 100 : 0;
        
        return Map.of(
            "totalTransactions", totalTransactions,
            "totalVolume", totalVolume,
            "avgTransactionValue", totalTransactions > 0 ? 
                totalVolume.divide(BigDecimal.valueOf(totalTransactions), 2, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO,
            "escrowStatusBreakdown", escrowStatusCounts,
            "transactionStatusBreakdown", transactionStatusCounts,
            "disputeRate", disputeRate,
            "dateRange", Map.of("start", defaultStart, "end", defaultEnd)
        );
    }

    /**
     * Get all transactions for admin with comprehensive filtering
     */    @Transactional(readOnly = true)
    public PagedResponse<Object> getAllTransactionsForAdmin(
            String status, String escrowStatus, Long buyerId, Long sellerId,
            LocalDateTime startDate, LocalDateTime endDate, String search, Pageable pageable) {
        
        // Step 1: Get transactions with regular joins (not JOIN FETCH) for filtering and pagination
        Page<Transaction> transactions = transactionRepository.findAll((root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Filter by transaction status
            if (status != null && !status.trim().isEmpty()) {
                try {
                    TransactionStatus transactionStatus = TransactionStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("status"), transactionStatus));
                } catch (IllegalArgumentException e) {
                    predicates.add(criteriaBuilder.disjunction()); // Return empty result for invalid status
                }
            }
            
            // Filter by escrow status
            if (escrowStatus != null && !escrowStatus.trim().isEmpty()) {
                try {
                    EscrowStatus escrowStat = EscrowStatus.valueOf(escrowStatus.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("escrowStatus"), escrowStat));
                } catch (IllegalArgumentException e) {
                    predicates.add(criteriaBuilder.disjunction());
                }
            }
            
            // Filter by buyer ID
            if (buyerId != null) {
                Join<Transaction, User> buyerJoin = root.join("buyer", JoinType.LEFT);
                predicates.add(criteriaBuilder.equal(buyerJoin.get("userId"), buyerId));
            }
            
            // Filter by seller ID
            if (sellerId != null) {
                Join<Transaction, User> sellerJoin = root.join("seller", JoinType.LEFT);
                predicates.add(criteriaBuilder.equal(sellerJoin.get("userId"), sellerId));
            }
            
            // Filter by date range
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            
            // Search functionality
            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.toLowerCase() + "%";
                Join<Transaction, User> buyerJoinForSearch = root.join("buyer", JoinType.LEFT);
                Join<Transaction, User> sellerJoinForSearch = root.join("seller", JoinType.LEFT);
                
                Predicate searchPredicate = criteriaBuilder.or(
                    criteriaBuilder.like(
                        criteriaBuilder.lower(buyerJoinForSearch.get("username")), 
                        searchPattern
                    ),
                    criteriaBuilder.like(
                        criteriaBuilder.lower(sellerJoinForSearch.get("username")), 
                        searchPattern
                    ),
                    criteriaBuilder.like(
                        criteriaBuilder.toString(root.get("transactionId")), 
                        searchPattern
                    )
                );
                predicates.add(searchPredicate);
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        }, pageable);
        
        // Step 2: If we have transactions, load the relationships separately to avoid N+1 queries
        if (transactions.hasContent()) {
            List<Long> transactionIds = transactions.getContent().stream()
                .map(Transaction::getTransactionId)
                .collect(Collectors.toList());
            
            // Fetch transactions with all relationships using batch loading
            List<Transaction> fullTransactions = transactionRepository.findByTransactionIdInWithAllRelationships(transactionIds);
            
            // Create a map for quick lookup
            Map<Long, Transaction> transactionMap = fullTransactions.stream()
                .collect(Collectors.toMap(Transaction::getTransactionId, Function.identity()));
            
            // Maintain the original order from pagination
            List<Object> transactionResponses = transactions.getContent().stream()
                .map(t -> transactionMap.get(t.getTransactionId()))
                .filter(Objects::nonNull)
                .map(this::convertToTransactionAdminResponse)
                .collect(Collectors.toList());
            
            // Create a new Page with the converted responses
            Page<Object> responsePage = new PageImpl<>(
                transactionResponses, 
                pageable, 
                transactions.getTotalElements()
            );
            
            return PagedResponse.of(responsePage);
        } else {
            // No transactions found, return empty page
            Page<Object> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);
            return PagedResponse.of(emptyPage);
        }
    }

    /**
     * Get problem transactions that need admin attention
     */
    @Transactional(readOnly = true)
    public Object getProblemTransactions() {
        // Find transactions with issues
        List<Transaction> disputedTransactions = transactionRepository.findByDisputeStatus(DisputeStatus.OPEN);
        List<Transaction> failedTransfers = transactionRepository.findByEscrowStatus(EscrowStatus.TRANSFER_FAILED);
        List<Transaction> stuckEscrows = transactionRepository.findByEscrowStatusAndCreatedAtBefore(
            EscrowStatus.HOLDING, LocalDateTime.now().minusDays(30)
        );
        
        return Map.of(
            "disputedTransactions", disputedTransactions.stream()
                .map(this::convertToTransactionAdminResponse)
                .collect(Collectors.toList()),
            "failedTransfers", failedTransfers.stream()
                .map(this::convertToTransactionAdminResponse)
                .collect(Collectors.toList()),
            "stuckEscrows", stuckEscrows.stream()
                .map(this::convertToTransactionAdminResponse)
                .collect(Collectors.toList()),
            "summary", Map.of(
                "disputedCount", disputedTransactions.size(),
                "failedTransferCount", failedTransfers.size(),
                "stuckEscrowCount", stuckEscrows.size(),
                "totalProblems", disputedTransactions.size() + failedTransfers.size() + stuckEscrows.size()
            )
        );
    }

    /**
     * Force release escrow for a specific transaction
     */
    @Transactional
    public void forceReleaseEscrow(Long transactionId, String notes, Long adminId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        
        if (transaction.getEscrowStatus() != EscrowStatus.HOLDING) {
            throw new BusinessLogicException("Cannot release escrow - transaction is not in HOLDING status");
        }
        
        try {
            // Release payment to seller through Stripe
            stripeService.releasePaymentToSeller(transaction.getOrderItem(), transaction);
            
            // Update transaction status
            transaction.setEscrowStatus(EscrowStatus.TRANSFERRED);
            transactionRepository.save(transaction);
            
            log.info("Admin {} force released escrow for transaction {}. Notes: {}", 
                    adminId, transactionId, notes);
                    
        } catch (Exception e) {
            log.error("Failed to force release escrow for transaction {}: {}", transactionId, e.getMessage());
            throw new BusinessLogicException("Failed to release escrow: " + e.getMessage());
        }
    }

    /**
     * Get revenue analytics for admin dashboard
     */
    @Transactional(readOnly = true)
    public Object getRevenueAnalytics(String period, int periods) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate;
        
        // Calculate start date based on period
        switch (period.toLowerCase()) {
            case "daily" -> startDate = endDate.minusDays(periods);
            case "weekly" -> startDate = endDate.minusWeeks(periods);
            case "monthly" -> startDate = endDate.minusMonths(periods);
            default -> throw new BusinessLogicException("Invalid period: " + period);
        }
        
        // Get completed transactions within date range
        List<Transaction> completedTransactions = transactionRepository
                .findByStatusAndCreatedAtBetween(TransactionStatus.DELIVERED, startDate, endDate);
        
        // Calculate total revenue and platform fees
        BigDecimal totalRevenue = completedTransactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalPlatformFees = completedTransactions.stream()
                .map(Transaction::getPlatformFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Group by time period for trend analysis
        Map<String, BigDecimal> revenueByPeriod = new LinkedHashMap<>();
        Map<String, BigDecimal> feesByPeriod = new LinkedHashMap<>();
        
        // This would need more complex grouping logic based on the period type
        // For now, return aggregated data
        
        return Map.of(
            "totalRevenue", totalRevenue,
            "totalPlatformFees", totalPlatformFees,
            "netRevenue", totalRevenue.subtract(totalPlatformFees),
            "transactionCount", completedTransactions.size(),
            "avgOrderValue", completedTransactions.size() > 0 ? 
                totalRevenue.divide(BigDecimal.valueOf(completedTransactions.size()), 2, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO,
            "period", period,
            "periods", periods,
            "dateRange", Map.of("start", startDate, "end", endDate),
            "revenueByPeriod", revenueByPeriod,
            "feesByPeriod", feesByPeriod
        );
    }    /**
     * Get seller performance metrics
     */
    @Transactional(readOnly = true)
    public PagedResponse<Object> getSellerPerformanceMetrics(String sortBy, int days, Pageable pageable) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        // Get users who have transactions as sellers in the specified period
        List<Transaction> recentTransactions = transactionRepository.findByCreatedAtBetween(startDate, LocalDateTime.now());
        Set<Long> sellerIds = recentTransactions.stream()
                .map(t -> t.getSeller().getUserId())
                .collect(Collectors.toSet());
        
        if (sellerIds.isEmpty()) {
            // No sellers in the period, return empty result
            return PagedResponse.of(Page.empty(pageable));
        }
        
        // Get the sellers and calculate metrics
        List<User> allSellers = userRepository.findAllById(sellerIds);
        
        List<Object> sellerMetrics = allSellers.stream().map(seller -> {
            List<Transaction> sellerTransactions = transactionRepository
                    .findBySellerAndCreatedAtAfter(seller, startDate);
            
            long totalSales = sellerTransactions.size();
            BigDecimal totalVolume = sellerTransactions.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            long successfulSales = sellerTransactions.stream()
                    .mapToLong(t -> t.getStatus() == TransactionStatus.DELIVERED ? 1 : 0)
                    .sum();
            
            double successRate = totalSales > 0 ? (double) successfulSales / totalSales * 100 : 0;
            
            long disputes = sellerTransactions.stream()
                    .mapToLong(t -> t.getDisputeStatus() != null && t.getDisputeStatus() == DisputeStatus.OPEN ? 1 : 0)
                    .sum();
            
            double disputeRate = totalSales > 0 ? (double) disputes / totalSales * 100 : 0;
            
            return Map.of(
                "sellerId", seller.getUserId(),
                "username", seller.getUsername(),
                "totalSales", totalSales,
                "totalVolume", totalVolume,
                "successRate", successRate,
                "disputeRate", disputeRate,
                "avgOrderValue", totalSales > 0 ? 
                    totalVolume.divide(BigDecimal.valueOf(totalSales), 2, RoundingMode.HALF_UP) : 
                    BigDecimal.ZERO
            );
        }).collect(Collectors.toList());
        
        // Sort based on sortBy parameter
        switch (sortBy.toLowerCase()) {
            case "total_volume":
                sellerMetrics.sort((a, b) -> ((BigDecimal) ((Map<?, ?>) b).get("totalVolume"))
                        .compareTo((BigDecimal) ((Map<?, ?>) a).get("totalVolume")));
                break;
            case "success_rate":
                sellerMetrics.sort((a, b) -> Double.compare(
                        (Double) ((Map<?, ?>) b).get("successRate"),
                        (Double) ((Map<?, ?>) a).get("successRate")));
                break;
            case "dispute_rate":
                sellerMetrics.sort((a, b) -> Double.compare(
                        (Double) ((Map<?, ?>) a).get("disputeRate"),
                        (Double) ((Map<?, ?>) b).get("disputeRate")));
                break;
            default: // sales_volume
                sellerMetrics.sort((a, b) -> Long.compare(
                        (Long) ((Map<?, ?>) b).get("totalSales"),
                        (Long) ((Map<?, ?>) a).get("totalSales")));
        }
        
        // Manual pagination
        int start = pageable.getPageNumber() * pageable.getPageSize();
        int end = Math.min(start + pageable.getPageSize(), sellerMetrics.size());
        List<Object> pageContent = start < sellerMetrics.size() ? 
                sellerMetrics.subList(start, end) : new ArrayList<>();
        
        Page<Object> page = new PageImpl<>(pageContent, pageable, sellerMetrics.size());
        return PagedResponse.of(page);
    }

    /**
     * Get buyer behavior analytics
     */
    @Transactional(readOnly = true)
    public PagedResponse<Object> getBuyerBehaviorAnalytics(String sortBy, int days, Pageable pageable) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        Page<User> buyers = userRepository.findAll(pageable);
        
        Page<Object> buyerMetrics = buyers.map(buyer -> {
            List<Order> buyerOrders = orderRepository.findByBuyerAndCreatedAtAfter(buyer, startDate);
            
            BigDecimal totalSpent = buyerOrders.stream()
                    .filter(order -> order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.PAID)
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            long orderCount = buyerOrders.size();
            long cancelledOrders = buyerOrders.stream()
                    .mapToLong(o -> o.getStatus() == OrderStatus.CANCELLED ? 1 : 0)
                    .sum();
            
            double cancellationRate = orderCount > 0 ? (double) cancelledOrders / orderCount * 100 : 0;
            
            return Map.of(
                "buyerId", buyer.getUserId(),
                "username", buyer.getUsername(),
                "totalSpent", totalSpent,
                "orderCount", orderCount,
                "avgOrderValue", orderCount > 0 ? 
                    totalSpent.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP) : 
                    BigDecimal.ZERO,
                "cancellationRate", cancellationRate
            );
        });
        
        return PagedResponse.of(buyerMetrics);
    }

    /**
     * Export order data in specified format
     */
    @Transactional(readOnly = true)
    public byte[] exportOrderData(String status, LocalDateTime startDate, LocalDateTime endDate, String format) {
        // Get orders with filters
        List<Order> orders = orderRepository.findAll((root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (status != null && !status.trim().isEmpty()) {
                try {
                    OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("status"), orderStatus));
                } catch (IllegalArgumentException e) {
                    predicates.add(criteriaBuilder.disjunction());
                }
            }
            
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        });
        
        if ("csv".equals(format)) {
            return exportOrdersAsCsv(orders);
        } else if ("excel".equals(format)) {
            return exportOrdersAsExcel(orders);
        } else {
            throw new BusinessLogicException("Unsupported export format: " + format);
        }
    }

    /**
     * Bulk update order statuses
     */
    @Transactional
    public Object bulkUpdateOrderStatuses(Map<String, Object> request, Long adminId) {
        @SuppressWarnings("unchecked")
        List<Long> orderIds = (List<Long>) request.get("orderIds");
        String newStatus = (String) request.get("status");
        String notes = (String) request.get("notes");
        
        if (orderIds == null || orderIds.isEmpty()) {
            throw new BusinessLogicException("Order IDs are required for bulk update");
        }
        
        if (newStatus == null || newStatus.trim().isEmpty()) {
            throw new BusinessLogicException("Status is required for bulk update");
        }
        
        OrderStatus orderStatus;
        try {
            orderStatus = OrderStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessLogicException("Invalid order status: " + newStatus);
        }
        
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (Long orderId : orderIds) {
            try {
                forceUpdateOrderStatus(orderId, newStatus, notes, adminId);
                successCount++;
            } catch (Exception e) {
                failCount++;
                errors.add("Order " + orderId + ": " + e.getMessage());
            }
        }
        
        return Map.of(
            "processed", orderIds.size(),
            "successful", successCount,
            "failed", failCount,
            "errors", errors
        );
    }

    /**
     * Get order timeline for debugging
     */
    @Transactional(readOnly = true)
    public Object getOrderTimeline(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));
        
        List<Map<String, Object>> timeline = new ArrayList<>();
        
        // Order creation
        timeline.add(Map.of(
           
            "timestamp", order.getCreatedAt(),
            "event", "ORDER_CREATED",
            "description", "Order created",
            "status", "PENDING"
        ));
        
        // Payment events
        if (order.getPaidAt() != null) {
            timeline.add(Map.of(
                "timestamp", order.getPaidAt(),
                "event", "PAYMENT_CONFIRMED",
                "description", "Payment confirmed",
                "status", "PAID"
            ));
        }
        
        // Shipping events
        if (order.getShippedAt() != null) {
            timeline.add(Map.of(
                "timestamp", order.getShippedAt(),
                "event", "ORDER_SHIPPED",
                "description", "Order shipped",
                "status", "SHIPPED"
            ));
        }
        
        // Delivery events
        if (order.getDeliveredAt() != null) {
            timeline.add(Map.of(
                "timestamp", order.getDeliveredAt(),
                "event", "ORDER_DELIVERED",
                "description", "Order delivered",
                "status", "DELIVERED"
            ));
        }
        
        // Add transaction events
        for (OrderItem item : order.getOrderItems()) {
            Optional<Transaction> transactionOpt = getTransactionForOrderItemSafely(item.getOrderItemId());
            if (transactionOpt.isPresent()) {
                Transaction transaction = transactionOpt.get();
                
                if (transaction.getShippedAt() != null) {
                    timeline.add(Map.of(
                        "timestamp", transaction.getShippedAt(),
                        "event", "ITEM_SHIPPED",
                        "description", "Item shipped: " + item.getProduct().getTitle(),
                        "trackingNumber", transaction.getTrackingNumber() != null ? transaction.getTrackingNumber() : "",
                        "itemId", item.getOrderItemId()
                    ));
                }
                
                if (transaction.getDeliveredAt() != null) {
                    timeline.add(Map.of(
                        "timestamp", transaction.getDeliveredAt(),
                        "event", "ITEM_DELIVERED",
                        "description", "Item delivered: " + item.getProduct().getTitle(),
                        "itemId", item.getOrderItemId()
                    ));
                }
            }
        }
        
        // Sort timeline by timestamp
        timeline.sort((a, b) -> ((LocalDateTime) a.get("timestamp")).compareTo((LocalDateTime) b.get("timestamp")));
        
        return Map.of(
            "orderId", orderId,
            "currentStatus", order.getStatus().name(),
            "timeline", timeline,
            "summary", Map.of(
                "totalEvents", timeline.size(),
                "orderAge", java.time.Duration.between(order.getCreatedAt(), LocalDateTime.now()).toDays() + " days"
            )
        );
    }

    // Helper methods for export functionality
    private byte[] exportOrdersAsCsv(List<Order> orders) {
        StringBuilder csv = new StringBuilder();
        csv.append("Order ID,Status,Total Amount,Buyer,Created At,Paid At,Delivered At\n");
        
        for (Order order : orders) {
            csv.append(order.getOrderId()).append(",")
               .append(order.getStatus()).append(",")
               .append(order.getTotalAmount()).append(",")
               .append(order.getBuyer().getUsername()).append(",")
               .append(order.getCreatedAt()).append(",")
               .append(order.getPaidAt() != null ? order.getPaidAt() : "").append(",")
               .append(order.getDeliveredAt() != null ? order.getDeliveredAt() : "").append("\n");
        }
        
        return csv.toString().getBytes();    }
    
    private byte[] exportOrdersAsExcel(List<Order> orders) {
        // For now, return CSV format - Excel export would require Apache POI library
        return exportOrdersAsCsv(orders);
    }
    
    /**
     * Sort transactions according to the provided Sort specification
     */
    private List<Transaction> sortTransactions(List<Transaction> transactions, Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return transactions;
        }
        
        Comparator<Transaction> comparator = null;
        
        for (Sort.Order order : sort) {
            Comparator<Transaction> fieldComparator = null;
            
            switch (order.getProperty()) {
                case "transactionId":
                    fieldComparator = Comparator.comparing(Transaction::getTransactionId);
                    break;
                case "amount":
                    fieldComparator = Comparator.comparing(Transaction::getAmount);
                    break;
                case "createdAt":
                    fieldComparator = Comparator.comparing(Transaction::getCreatedAt);
                    break;
                case "status":
                    fieldComparator = Comparator.comparing(t -> t.getStatus().name());
                    break;
                case "escrowStatus":
                    fieldComparator = Comparator.comparing(t -> t.getEscrowStatus().name());
                    break;
                case "buyer.username":
                    fieldComparator = Comparator.comparing(t -> t.getBuyer().getUsername());
                    break;
                case "seller.username":
                    fieldComparator = Comparator.comparing(t -> t.getSeller().getUsername());
                    break;
                default:
                    // Default to createdAt if unknown property
                    fieldComparator = Comparator.comparing(Transaction::getCreatedAt);
                    break;
            }
            
            if (order.isDescending()) {
                fieldComparator = fieldComparator.reversed();
            }
            
            if (comparator == null) {
                comparator = fieldComparator;
            } else {
                comparator = comparator.thenComparing(fieldComparator);
            }
        }
        
        return transactions.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }
    
    /**
     * Convert transaction to admin response format
     */
    private Object convertToTransactionAdminResponse(Transaction transaction) {
        Map<String, Object> response = new HashMap<>();
        response.put("transactionId", transaction.getTransactionId());
        response.put("amount", transaction.getAmount());
        response.put("platformFee", transaction.getPlatformFee());
        response.put("status", transaction.getStatus().name());
        response.put("escrowStatus", transaction.getEscrowStatus().name());
        response.put("disputeStatus", transaction.getDisputeStatus() != null ? transaction.getDisputeStatus().name() : "NONE");
        response.put("buyer", Map.of(
            "userId", transaction.getBuyer().getUserId(),
            "username", transaction.getBuyer().getUsername()
        ));
        response.put("seller", Map.of(
            "userId", transaction.getSeller().getUserId(),
            "username", transaction.getSeller().getUsername()
        ));
        response.put("orderId", transaction.getOrderItem().getOrder().getOrderId());
        response.put("productTitle", transaction.getOrderItem().getProduct().getTitle());
        response.put("createdAt", transaction.getCreatedAt());
        response.put("shippedAt", transaction.getShippedAt());
        response.put("deliveredAt", transaction.getDeliveredAt());
        response.put("trackingNumber", transaction.getTrackingNumber());
        return response;
    }
}
