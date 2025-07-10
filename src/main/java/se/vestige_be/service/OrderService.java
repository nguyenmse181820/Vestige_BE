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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.JoinType;
import se.vestige_be.dto.request.OrderCreateRequest;
import se.vestige_be.dto.request.OrderStatusUpdateRequest;
import se.vestige_be.dto.response.*;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.exception.ResourceNotFoundException;
import se.vestige_be.exception.UnauthorizedException;
import se.vestige_be.pojo.*;
import se.vestige_be.pojo.enums.*;
import se.vestige_be.repository.*;
import se.vestige_be.mapper.OrderMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.Comparator;
import java.util.Optional;
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
    private final OrderMapper orderMapper;

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
        order.setStatus(OrderStatus.PROCESSING);
        order.setPaidAt(LocalDateTime.now());        
        order.getOrderItems().forEach(item -> {
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
            case AWAITING_PICKUP -> handleItemAwaitingPickup(orderItem);
            case IN_WAREHOUSE -> handleItemInWarehouse(orderItem); // Assuming this will be called from a future LogisticsService
            case OUT_FOR_DELIVERY -> handleItemOutForDelivery(orderItem, request);
            case DELIVERED -> handleItemDelivered(orderItem);
            case CANCELLED -> handleItemCancelled(orderItem);
            default -> throw new BusinessLogicException("The status update for '" + newStatus + "' is not supported here.");
        }

        updateOverallOrderStatus(order);
        order = orderRepository.save(order);

        return convertToDetailResponse(order);
    }

    // cancelOrder method was removed as per refactoring requirement.
    // Cancellations now happen at the individual item level through updateOrderItemStatus.

    public PagedResponse<OrderListResponse> getUserOrders(Long userId, String status, String role, Pageable pageable) {
    Page<Order> orders;

        if ("seller".equalsIgnoreCase(role)) {
        orders = getSellerOrders(userId, status, pageable);
    } else {
        orders = getBuyerOrders(userId, status, pageable);
    }

    // Load product images separately to avoid MultipleBagFetchException
    loadProductImagesForOrders(orders.getContent());

    Page<OrderListResponse> orderResponses = orders.map(orderMapper::convertToListResponse);
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

        Page<OrderListResponse> orderResponses = orders.map(orderMapper::convertToListResponse);
        return PagedResponse.of(orderResponses);
    }

    private void handleItemAwaitingPickup(OrderItem orderItem) {
        orderItem.setStatus(OrderItemStatus.AWAITING_PICKUP);
        log.info("OrderItem {} is now AWAITING_PICKUP.", orderItem.getOrderItemId());
    }

    private void handleItemInWarehouse(OrderItem orderItem) {
        orderItem.setStatus(OrderItemStatus.IN_WAREHOUSE);
        Transaction transaction = getTransactionForOrderItem(orderItem.getOrderItemId());
        // Generate an internal tracking number
        transaction.setTrackingNumber("VSTG-" + orderItem.getOrderItemId());
        transactionRepository.save(transaction);
        log.info("OrderItem {} is now IN_WAREHOUSE.", orderItem.getOrderItemId());
    }

    private void handleItemOutForDelivery(OrderItem orderItem, OrderStatusUpdateRequest request) {
        orderItem.setStatus(OrderItemStatus.OUT_FOR_DELIVERY);
        Transaction transaction = getTransactionForOrderItem(orderItem.getOrderItemId());
        transaction.setShippedAt(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.SHIPPED); // Use SHIPPED for transaction compatibility
        transactionRepository.save(transaction);
        log.info("OrderItem {} is now OUT_FOR_DELIVERY.", orderItem.getOrderItemId());
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
            case PROCESSING -> order.setPaidAt(now);
            case OUT_FOR_DELIVERY -> order.setShippedAt(now);
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
     * Provides comprehensive statistics for admin dashboard including user metrics,
     * order trends, financial data, and performance indicators.
     *
     * @param period Period for trends analysis (daily, weekly, monthly)
     * @param periods Number of periods to include in trend data
     * @param startDate Optional start date for filtering
     * @param endDate Optional end date for filtering
     * @return Map containing comprehensive system statistics
     */
    public Object getComprehensiveSystemStatistics(String period, int periods, LocalDateTime startDate, LocalDateTime endDate) {
        // Set default dates if not provided
        LocalDateTime start = startDate != null ? startDate : LocalDateTime.now().minusMonths(12);
        LocalDateTime end = endDate != null ? endDate : LocalDateTime.now();

        // 1. Order metrics
        Map<OrderStatus, Long> statusCounts = Arrays.stream(OrderStatus.values())
                .collect(Collectors.toMap(
                        status -> status,
                        orderRepository::countByStatus,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        long totalOrders = orderRepository.count();
        long totalCompletedOrders = orderRepository.countByStatus(OrderStatus.DELIVERED);
        long totalCancelledOrders = orderRepository.countByStatus(OrderStatus.CANCELLED);

        // 2. Financial metrics
        BigDecimal totalRevenue = orderRepository.findByStatus(OrderStatus.DELIVERED)
                .stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPlatformFees = orderItemRepository.findByStatus(OrderItemStatus.DELIVERED)
                .stream()
                .map(OrderItem::getPlatformFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate average order value
        BigDecimal avgOrderValue = totalCompletedOrders > 0 ?
                totalRevenue.divide(BigDecimal.valueOf(totalCompletedOrders), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // 3. User metrics
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByAccountStatus("active");
        long sellersWithCompletedSales = userRepository.countDistinctSellersByOrderItemStatus(OrderItemStatus.DELIVERED);

        // 4. Transaction metrics
        long pendingEscrows = transactionRepository.countByEscrowStatus(EscrowStatus.HOLDING);
        long disputedTransactions = transactionRepository.countByDisputeStatus(DisputeStatus.OPEN);

        // 5. Generate trend data based on period
        List<Map<String, Object>> trends = generateTrendData(period, periods, start, end);

        // 6. Performance metrics
        double completionRate = totalOrders > 0 ?
                (double)totalCompletedOrders / totalOrders * 100 : 0;
        double cancellationRate = totalOrders > 0 ?
                (double)totalCancelledOrders / totalOrders * 100 : 0;

        // 7. Category analysis - top selling categories
        List<Map<String, Object>> topCategories = getTopSellingCategories(5, start, end);

        // 8. Brand analysis - top selling brands
        List<Map<String, Object>> topBrands = getTopSellingBrands(5, start, end);

        // Build the comprehensive response
        Map<String, Object> result = new LinkedHashMap<>();

        // Summary metrics
        Map<String, Object> summaryMap = new HashMap<>();
        summaryMap.put("totalOrders", totalOrders);
        summaryMap.put("completedOrders", totalCompletedOrders);
        summaryMap.put("cancelledOrders", totalCancelledOrders);
        summaryMap.put("totalRevenue", totalRevenue);
        summaryMap.put("platformFeesCollected", totalPlatformFees);
        summaryMap.put("avgOrderValue", avgOrderValue);
        summaryMap.put("totalUsers", totalUsers);
        summaryMap.put("activeUsers", activeUsers);
        summaryMap.put("activeSellers", sellersWithCompletedSales);
        result.put("summary", summaryMap);

        // Status breakdown
        result.put("orderStatusBreakdown", statusCounts);

        // Performance metrics
        Map<String, Object> performanceMap = new HashMap<>();
        performanceMap.put("orderCompletionRate", completionRate);
        performanceMap.put("orderCancellationRate", cancellationRate);
        performanceMap.put("pendingEscrows", pendingEscrows);
        performanceMap.put("disputedTransactions", disputedTransactions);
        result.put("performance", performanceMap);

        // Trend data
        Map<String, Object> trendMap = new HashMap<>();
        trendMap.put("period", period);
        trendMap.put("data", trends);
        result.put("trends", trendMap);

        // Top categories and brands
        result.put("topCategories", topCategories);
        result.put("topBrands", topBrands);

        return result;
    }

    /**
     * Generates trend data for orders and revenue based on the specified period
     */
    private List<Map<String, Object>> generateTrendData(String period, int periods, LocalDateTime start, LocalDateTime end) {
        List<Map<String, Object>> trends = new ArrayList<>();

        LocalDateTime currentPeriodStart = end;

        // Determine the period function and format
        Function<LocalDateTime, LocalDateTime> periodStartFunc;
        Function<LocalDateTime, LocalDateTime> periodEndFunc;
        Function<LocalDateTime, String> periodLabelFunc;

        switch (period.toLowerCase()) {
            case "daily":
                periodStartFunc = dt -> dt.withHour(0).withMinute(0).withSecond(0).withNano(0);
                periodEndFunc = dt -> dt.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1);
                periodLabelFunc = dt -> dt.toLocalDate().toString();
                // Move back to start of current day
                currentPeriodStart = periodStartFunc.apply(end);
                // Move back periods-1 days (to get 'periods' total days including today)
                currentPeriodStart = currentPeriodStart.minusDays(periods - 1);
                break;

            case "weekly":
                periodStartFunc = dt -> dt.withHour(0).withMinute(0).withSecond(0).withNano(0)
                        .minusDays(dt.getDayOfWeek().getValue() - 1); // Start of week (Monday)
                periodEndFunc = dt -> periodStartFunc.apply(dt).plusWeeks(1);
                periodLabelFunc = dt -> "Week " + dt.toLocalDate().toString();
                // Move to start of current week
                currentPeriodStart = periodStartFunc.apply(end);
                // Move back periods-1 weeks
                currentPeriodStart = currentPeriodStart.minusWeeks(periods - 1);
                break;

            case "monthly":
            default:
                periodStartFunc = dt -> dt.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                periodEndFunc = dt -> periodStartFunc.apply(dt).plusMonths(1);
                periodLabelFunc = dt -> dt.getYear() + "-" + dt.getMonthValue();
                // Move to start of current month
                currentPeriodStart = periodStartFunc.apply(end);
                // Move back periods-1 months
                currentPeriodStart = currentPeriodStart.minusMonths(periods - 1);
                break;
        }

        // Ensure we don't go before the start date
        if (currentPeriodStart.isBefore(start)) {
            currentPeriodStart = periodStartFunc.apply(start);
        }

        // Generate data for each period
        LocalDateTime periodStart = currentPeriodStart;

        while (!periodStart.isAfter(end)) {
            LocalDateTime periodEnd = periodEndFunc.apply(periodStart);
            if (periodEnd.isAfter(end)) {
                periodEnd = end;
            }

            // Count orders in this period
            long periodOrders = orderRepository.countByCreatedAtBetween(periodStart, periodEnd);

            // Calculate revenue in this period
            BigDecimal periodRevenue = orderRepository.findByCreatedAtBetween(periodStart, periodEnd)
                    .stream()
                    .filter(order -> order.getStatus() == OrderStatus.PROCESSING ||
                            order.getStatus() == OrderStatus.OUT_FOR_DELIVERY ||
                            order.getStatus() == OrderStatus.DELIVERED)
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Calculate platform fees collected in this period
            BigDecimal periodFees = orderItemRepository.findByOrderCreatedAtBetween(periodStart, periodEnd)
                    .stream()
                    .filter(item -> item.getStatus() == OrderItemStatus.PROCESSING ||
                            item.getStatus() == OrderItemStatus.OUT_FOR_DELIVERY ||
                            item.getStatus() == OrderItemStatus.DELIVERED)
                    .map(OrderItem::getPlatformFee)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Count new users in this period
            long newUsers = userRepository.countByJoinedDateBetween(periodStart, periodEnd);

            // Add data for this period
            Map<String, Object> trendEntry = new HashMap<>();
            trendEntry.put("period", periodLabelFunc.apply(periodStart));
            trendEntry.put("orders", periodOrders);
            trendEntry.put("revenue", periodRevenue);
            trendEntry.put("fees", periodFees);
            trendEntry.put("newUsers", newUsers);
            trends.add(trendEntry);

            // Move to next period
            periodStart = periodEndFunc.apply(periodStart);
        }

        return trends;
    }

    /**
     * Gets the top selling categories within the date range
     */
    private List<Map<String, Object>> getTopSellingCategories(int limit, LocalDateTime start, LocalDateTime end) {
        return orderItemRepository.findByOrderCreatedAtBetween(start, end)
                .stream()
                .filter(item -> item.getStatus() == OrderItemStatus.DELIVERED)
                .filter(item -> item.getProduct() != null && item.getProduct().getCategory() != null)
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getCategory(),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<Category, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> categoryMap = new HashMap<>();
                    categoryMap.put("categoryId", entry.getKey().getCategoryId());
                    categoryMap.put("name", entry.getKey().getName());
                    categoryMap.put("count", entry.getValue());
                    return categoryMap;
                })
                .collect(Collectors.toList());
    }

    /**
     * Gets the top selling brands within the date range
     */
    private List<Map<String, Object>> getTopSellingBrands(int limit, LocalDateTime start, LocalDateTime end) {
        return orderItemRepository.findByOrderCreatedAtBetween(start, end)
                .stream()
                .filter(item -> item.getStatus() == OrderItemStatus.DELIVERED)
                .filter(item -> item.getProduct() != null && item.getProduct().getBrand() != null)
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getBrand(),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<Brand, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> brandMap = new HashMap<>();
                    brandMap.put("brandId", entry.getKey().getBrandId());
                    brandMap.put("name", entry.getKey().getName());
                    brandMap.put("count", entry.getValue());
                    return brandMap;
                })
                .collect(Collectors.toList());
    }
    /**
     * Webhook handlers
     */
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

        log.info("Updating order {} to PROCESSING.", order.getOrderId());
        order.setStatus(OrderStatus.PROCESSING);
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

    // Data class for order processing


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
        BigDecimal feePercentage = feeTierService.getFeePercentageForSeller(product.getSeller());

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
    // This validation is for actions performed by BUYERS and SELLERS.
    // Shipper actions are handled in the LogisticsService and are implicitly trusted.
    switch (newStatus) {
        case AWAITING_PICKUP:
            if (!isSeller) {
                throw new UnauthorizedException("Only the seller can request pickup for an item.");
            }
            if (currentStatus != OrderItemStatus.PROCESSING) {
                throw new BusinessLogicException("Cannot request pickup for an item that is not in PROCESSING status.");
            }
            break;

        case DELIVERED:
            if (!isBuyer) {
                throw new UnauthorizedException("Only the buyer can confirm an item's delivery.");
            }
            if (currentStatus != OrderItemStatus.OUT_FOR_DELIVERY) {
                throw new BusinessLogicException("Cannot confirm delivery for an item that is not yet out for delivery.");
            }
            break;

        case CANCELLED:
            if (!isItemCancellable(currentStatus)) {
                throw new BusinessLogicException("Item cannot be cancelled in its current status: " + currentStatus);
            }
            if (!isBuyer && !isSeller) {
                throw new UnauthorizedException("You are not authorized to cancel this item.");
            }
            break;

        // These statuses are managed by the internal logistics team and cannot be set directly by buyers or sellers.
        case IN_WAREHOUSE, OUT_FOR_DELIVERY:
            throw new UnauthorizedException("This status can only be updated by the Vestige shipping team.");

        default:
            // Prevents direct updates to other statuses like PROCESSING, PENDING, etc.
            throw new BusinessLogicException("The status " + newStatus + " cannot be set directly.");
    }
}

    private boolean isItemCancellable(OrderItemStatus status) {
        return status == OrderItemStatus.PENDING || status == OrderItemStatus.PROCESSING;
    }

    protected void updateOverallOrderStatus(Order order) {
        List<OrderItemStatus> itemStatuses = order.getOrderItems().stream()
                .map(OrderItem::getStatus)
                .toList();

        if (itemStatuses.isEmpty()) {
            order.setStatus(OrderStatus.CANCELLED);
            log.warn("Order {} has no items, setting status to CANCELLED.", order.getOrderId());
            return;
        }

        long totalItems = itemStatuses.size();
        long cancelledOrRefundedItems = itemStatuses.stream()
                .filter(s -> s == OrderItemStatus.CANCELLED || s == OrderItemStatus.REFUNDED)
                .count();

        // If all items are cancelled or refunded, the entire order is cancelled.
        if (cancelledOrRefundedItems == totalItems) {
            order.setStatus(OrderStatus.CANCELLED);
            log.info("All items in Order {} are cancelled/refunded. Setting order status to CANCELLED.", order.getOrderId());
            return;
        }

        // Filter out terminal statuses to find the "active" state of the order.
        Optional<OrderItemStatus> leastAdvancedActiveStatus = itemStatuses.stream()
                .filter(s -> s != OrderItemStatus.DELIVERED && s != OrderItemStatus.CANCELLED && s != OrderItemStatus.REFUNDED)
                .min(Comparator.comparing(Enum::ordinal));

        if (leastAdvancedActiveStatus.isPresent()) {
            // If there are still active items, the order's status is determined by the least-progressed item.
            OrderItemStatus minStatus = leastAdvancedActiveStatus.get();
            switch (minStatus) {
                case PENDING, PROCESSING, AWAITING_PICKUP, IN_WAREHOUSE ->
                    order.setStatus(OrderStatus.PROCESSING);
                case OUT_FOR_DELIVERY ->
                    order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
                default ->
                    // Fallback for any unexpected active status
                    order.setStatus(OrderStatus.PROCESSING);
            }
            log.info("Order {} status updated to {} based on least advanced item status: {}", order.getOrderId(), order.getStatus(), minStatus);
        } else {
            // If there are no active items, it means all non-cancelled items have been delivered.
            order.setStatus(OrderStatus.DELIVERED);
            log.info("All active items in Order {} are delivered. Setting order status to DELIVERED.", order.getOrderId());
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
protected OrderDetailResponse convertToDetailResponse(Order order) {
    return orderMapper.convertToDetailResponse(order);
}

private OrderListResponse convertToListResponse(Order order) {
    return orderMapper.convertToListResponse(order);
}

/**
 * Check the current status of a payment for debugging purposes
 */
public String checkPaymentStatus(String paymentIntentId) {
    if (paymentIntentId == null || paymentIntentId.isEmpty()) {
        throw new BusinessLogicException("Payment intent ID is required");
    }

    try {
        return stripeService.getPaymentIntentStatus(paymentIntentId);
    } catch (Exception e) {
        log.error("Failed to check payment status for payment intent {}: {}", paymentIntentId, e.getMessage());
        throw new BusinessLogicException("Failed to check payment status: " + e.getMessage());
    }
}

@Transactional
public OrderDetailResponse simulatePaymentCompletion(Long orderId, Long buyerId, String paymentIntentId) {
    log.warn("SIMULATING PAYMENT for order {} with paymentIntentId {}", orderId, paymentIntentId);

    Order order = getOrderWithValidation(orderId, buyerId, true);

    if (order.getStatus() != OrderStatus.PENDING) {
        throw new BusinessLogicException("Order is not in pending status");
    }

    // Update order to PROCESSING status
    order.setStripePaymentIntentId(paymentIntentId);
    order.setStatus(OrderStatus.PROCESSING);
    order.setPaidAt(LocalDateTime.now());

    // Update order items to PROCESSING and update product status
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
        transaction.setEscrowStatus(EscrowStatus.HOLDING);
        transactionRepository.save(transaction);
    }

    order = orderRepository.save(order);
    log.warn("SIMULATED PAYMENT COMPLETED for order {}", orderId);

    return convertToDetailResponse(order);
}

/**
 * Helper method to clean payment intent ID by removing client_secret part if present
 */
private String cleanPaymentIntentId(String rawPaymentIntentId) {
    if (rawPaymentIntentId == null) {
        return null;
    }

    // Payment intent IDs might be in format "pi_123456_secret_789012"
    // We only need the "pi_123456" part
    int underscoreSecretIndex = rawPaymentIntentId.indexOf("_secret_");
    if (underscoreSecretIndex > 0) {
        return rawPaymentIntentId.substring(0, underscoreSecretIndex);
    }

    return rawPaymentIntentId;
}

/**
 * Admin method to get paginated list of all transactions with comprehensive filtering options
 * For admin dashboard display
 */
@Transactional(readOnly = true)
public PagedResponse<Object> getAllTransactionsForAdmin(
        String status, String escrowStatus, Long buyerId, Long sellerId,
        LocalDateTime startDate, LocalDateTime endDate, String search, Pageable pageable) {

    // Use specification pattern for complex filtering
    Page<Transaction> transactions = transactionRepository.findAll((root, query, criteriaBuilder) -> {
        // Add JOIN FETCH for required relationships
        root.fetch("orderItem", JoinType.LEFT);
        root.fetch("buyer", JoinType.LEFT);
        root.fetch("seller", JoinType.LEFT);

        // Make the query DISTINCT to avoid duplicates from JOIN FETCH
        query.distinct(true);

        List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

        // Filter by transaction status
        if (status != null && !status.trim().isEmpty()) {
            try {
                TransactionStatus transactionStatus = TransactionStatus.valueOf(status.toUpperCase());
                predicates.add(criteriaBuilder.equal(root.get("status"), transactionStatus));
            } catch (IllegalArgumentException e) {
                // Invalid status - return empty result
                predicates.add(criteriaBuilder.disjunction());
            }
        }

        // Filter by escrow status
        if (escrowStatus != null && !escrowStatus.trim().isEmpty()) {
            try {
                EscrowStatus escrowStatusEnum = EscrowStatus.valueOf(escrowStatus.toUpperCase());
                predicates.add(criteriaBuilder.equal(root.get("escrowStatus"), escrowStatusEnum));
            } catch (IllegalArgumentException e) {
                // Invalid status - return empty result
                predicates.add(criteriaBuilder.disjunction());
            }
        }

        // Filter by buyer ID
        if (buyerId != null) {
            predicates.add(criteriaBuilder.equal(root.get("buyer").get("userId"), buyerId));
        }

        // Filter by seller ID
        if (sellerId != null) {
            predicates.add(criteriaBuilder.equal(root.get("seller").get("userId"), sellerId));
        }

        // Filter by date range
        if (startDate != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
        }
        if (endDate != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
        }

        // Search functionality (search in tracking number, dispute reason, etc.)
        if (search != null && !search.trim().isEmpty()) {
            String searchPattern = "%" + search.toLowerCase() + "%";
            jakarta.persistence.criteria.Predicate searchPredicate = criteriaBuilder.or(
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("trackingNumber")),
                            searchPattern
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("disputeReason")),
                            searchPattern
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("buyer").get("username")),
                            searchPattern
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("seller").get("username")),
                            searchPattern
                    )
            );
            predicates.add(searchPredicate);
        }

        return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    }, pageable);

    // Map to response DTOs
    List<Object> transactionResponses = transactions.getContent().stream()
            .map(transaction -> {
                // Get related order item and product info
                OrderItem orderItem = transaction.getOrderItem();
                Product product = orderItem != null ? orderItem.getProduct() : null;

                // Create nested maps
                Map<String, Object> buyerMap = new HashMap<>();
                buyerMap.put("userId", transaction.getBuyer().getUserId());
                buyerMap.put("username", transaction.getBuyer().getUsername());

                Map<String, Object> sellerMap = new HashMap<>();
                sellerMap.put("userId", transaction.getSeller().getUserId());
                sellerMap.put("username", transaction.getSeller().getUsername());

                Map<String, Object> orderMap = new HashMap<>();
                orderMap.put("orderId", orderItem != null ? orderItem.getOrder().getOrderId() : null);
                orderMap.put("orderItemId", orderItem != null ? orderItem.getOrderItemId() : null);

                Map<String, Object> productMap = null;
                if (product != null) {
                    productMap = new HashMap<>();
                    productMap.put("productId", product.getProductId());
                    productMap.put("title", product.getTitle());
                }

                Map<String, Object> trackingMap = new HashMap<>();
                trackingMap.put("trackingNumber", transaction.getTrackingNumber());
                trackingMap.put("trackingUrl", transaction.getTrackingUrl());
                trackingMap.put("shippedAt", transaction.getShippedAt());
                trackingMap.put("deliveredAt", transaction.getDeliveredAt());

                Map<String, Object> stripeMap = new HashMap<>();
                stripeMap.put("paymentIntentId", transaction.getStripePaymentIntentId());
                stripeMap.put("transferId", transaction.getStripeTransferId());

                Map<String, Object> disputeMap = new HashMap<>();
                disputeMap.put("status", transaction.getDisputeStatus());
                disputeMap.put("reason", transaction.getDisputeReason());

                // Create and populate the main response map
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("transactionId", transaction.getTransactionId());
                responseMap.put("status", transaction.getStatus());
                responseMap.put("escrowStatus", transaction.getEscrowStatus());
                responseMap.put("amount", transaction.getAmount());
                responseMap.put("platformFee", transaction.getPlatformFee());
                responseMap.put("createdAt", transaction.getCreatedAt());
                responseMap.put("buyer", buyerMap);
                responseMap.put("seller", sellerMap);
                responseMap.put("order", orderMap);
                responseMap.put("product", productMap);
                responseMap.put("tracking", trackingMap);
                responseMap.put("stripe", stripeMap);
                responseMap.put("dispute", disputeMap);

                return responseMap;
            })
            .collect(Collectors.toList());

    return PagedResponse.of(
            new PageImpl<>(transactionResponses, pageable, transactions.getTotalElements())
    );
}

/**
 * Admin method to get transactions that require attention
 */
@Transactional(readOnly = true)
public Object getProblemTransactions() {
    // Find transactions with issues
    List<Transaction> disputedTransactions = transactionRepository.findByDisputeStatusNot(DisputeStatus.NONE);
    List<Transaction> transferFailedTransactions = transactionRepository.findByEscrowStatus(EscrowStatus.TRANSFER_FAILED);

    // Find stuck escrows (delivered for more than 14 days but escrow still not released)
    LocalDateTime fourteenDaysAgo = LocalDateTime.now().minusDays(14);
    List<Transaction> stuckEscrowTransactions = transactionRepository.findByStatusAndDeliveredAtBeforeAndEscrowStatus(
            TransactionStatus.DELIVERED,
            fourteenDaysAgo,
            EscrowStatus.HOLDING
    );

    // Format response
    Map<String, Object> result = new HashMap<>();
    result.put("disputed", disputedTransactions.stream()
            .map(orderMapper::formatTransactionSummary)
            .collect(Collectors.toList()));

    result.put("transferFailed", transferFailedTransactions.stream()
            .map(orderMapper::formatTransactionSummary)
            .collect(Collectors.toList()));

    result.put("stuckEscrows", stuckEscrowTransactions.stream()
            .map(orderMapper::formatTransactionSummary)
            .collect(Collectors.toList()));

    return result;
}

/**
 * Format transaction summary for problem transactions - delegated to OrderMapper
 */
private Map<String, Object> formatTransactionSummary(Transaction transaction) {
    return orderMapper.formatTransactionSummary(transaction);
}

/**
 * Admin method to manually release funds from escrow to seller for a specific transaction
 */
@Transactional
public void forceReleaseEscrow(Long transactionId, String notes, Long adminId) {
    Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));

    if (transaction.getEscrowStatus() != EscrowStatus.HOLDING) {
        throw new BusinessLogicException("Cannot release escrow: current status is " + transaction.getEscrowStatus());
    }

    try {
        // Attempt transfer to seller
        BigDecimal sellerAmount = transaction.getAmount().subtract(transaction.getPlatformFee());
        User seller = transaction.getSeller();

        // If seller has Stripe account, initiate transfer
        if (seller.getStripeAccountId() != null) {
            String transferId = stripeService.transferToSeller(
                    sellerAmount,
                    seller.getStripeAccountId(),
                    transaction.getTransactionId()
            );
            transaction.setStripeTransferId(transferId);
            log.info("Admin {} force released escrow for transaction {}. Transfer ID: {}",
                    adminId, transactionId, transferId);
        } else {
            log.warn("Admin {} force released escrow for transaction {} but seller has no Stripe account",
                    adminId, transactionId);
        }

        // Update escrow status regardless of transfer outcome
        transaction.setEscrowStatus(EscrowStatus.RELEASED);
        transaction = transactionRepository.save(transaction);

        log.info("Admin {} successfully force released escrow for transaction {}", adminId, transactionId);
    } catch (Exception e) {
        log.error("Admin escrow release failed for transaction {}: {}", transactionId, e.getMessage());
        throw new BusinessLogicException("Escrow release failed: " + e.getMessage());
    }
}

/**
 * Adjust pageable for seller orders to handle potential multiplicity issues
 * We fetch more items to account for filtering them by order
 */
private Pageable adjustPageableForSellerOrders(Pageable pageable) {
    // Increase page size to account for filtering, but keep original page number
    int adjustedSize = pageable.getPageSize() * 3; // Fetch more items to account for grouping

    // Create new pageable with same sort but larger page size
    return PageRequest.of(
            pageable.getPageNumber(),
            adjustedSize,
            pageable.getSort()
    );
}

/**
 * Load product images separately to avoid MultipleBagFetchException
 * Spring Data JPA cannot fetch multiple collection relationships in a single query
 */
private void loadProductImagesForOrders(List<Order> orders) {
    if (orders == null || orders.isEmpty()) {
        return;
    }

    // Collect all products from order items
    List<Long> productIds = orders.stream()
            .flatMap(order -> order.getOrderItems().stream())
            .map(orderItem -> orderItem.getProduct().getProductId())
            .distinct()
            .collect(Collectors.toList());

    if (!productIds.isEmpty()) {
        // Fetch all products with their images in a separate query
        List<Product> productsWithImages = productRepository.findByProductIdInWithImages(productIds);

        // Create a map for faster lookup
        Map<Long, Product> productMap = productsWithImages.stream()
                .collect(Collectors.toMap(Product::getProductId, Function.identity()));

        // Replace product references in order items with the ones that have images loaded
        for (Order order : orders) {
            for (OrderItem orderItem : order.getOrderItems()) {
                Product productWithImages = productMap.get(orderItem.getProduct().getProductId());
                if (productWithImages != null) {
                    orderItem.setProduct(productWithImages);
                }
            }
        }

        log.debug("Loaded images for {} products", productsWithImages.size());
    }
}

/**
 * Admin-only method to update any order regardless of normal business rules
 *
 * @param userId User ID who owns the order
 * @param orderId Order ID to update
 * @param status New status to set
 * @param notes Admin notes about the change
 * @param forceUpdate Whether to bypass business rules
 * @param adminId Admin user ID making the change
 * @return Updated order details
 */
@Transactional
public OrderDetailResponse adminUpdateUserOrder(Long userId, Long orderId, String status,
                                                String notes, boolean forceUpdate, Long adminId) {
    // Find the order
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

    // Validate that the order belongs to the specified user
    if (!order.getBuyer().getUserId().equals(userId)) {
        throw new UnauthorizedException("Order does not belong to specified user");
    }

    // Get admin user for audit logs
    User admin = userRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin user not found with ID: " + adminId));

    // Parse the target status
    OrderStatus targetStatus;
    try {
        targetStatus = OrderStatus.valueOf(status.toUpperCase());
    } catch (IllegalArgumentException e) {
        throw new BusinessLogicException("Invalid order status: " + status);
    }

    // Current status for logging
    OrderStatus currentStatus = order.getStatus();

    // Validate status transition if not forcing update
    if (!forceUpdate) {
        validateStatusTransition(currentStatus, targetStatus);
    }

    // Perform status-specific actions
    switch (targetStatus) {
        case PROCESSING:
            if (order.getPaidAt() == null) {
                order.setPaidAt(LocalDateTime.now());
            }
            break;

        case OUT_FOR_DELIVERY:
            // Update all order items to out for delivery status
            if (order.getShippedAt() == null) {
                order.setShippedAt(LocalDateTime.now());
            }
            break;

        case DELIVERED:
            if (order.getDeliveredAt() == null) {
                order.setDeliveredAt(LocalDateTime.now());
            }
            break;

        case CANCELLED:
            // Handle cancellation if it was previously paid and processed
            if (currentStatus == OrderStatus.PROCESSING ||
                    currentStatus == OrderStatus.OUT_FOR_DELIVERY) {
                // Initiate refund process if order was paid
                if (order.getStripePaymentIntentId() != null) {
                    try {
                        // Create refund amount for full order total
                        BigDecimal refundAmount = order.getTotalAmount();
                        stripeService.refundPayment(order.getStripePaymentIntentId(), refundAmount);
                    } catch (Exception e) {
                        if (!forceUpdate) {
                            throw new BusinessLogicException("Failed to process refund: " + e.getMessage() +
                                    ". Use forceUpdate=true to bypass refund check.");
                        }
                        // Log the error but proceed if forceUpdate is true
                        log.error("Failed to process refund for forced order cancellation. Order ID: {}, Error: {}",
                                orderId, e.getMessage());
                    }
                }
            }
            break;

        default:
            // No special action for other statuses
            break;
    }

    // Update order status
    order.setStatus(targetStatus);

    // Save order
    order = orderRepository.save(order);

    if (forceUpdate) {
        OrderItemStatus itemStatus = mapOrderToItemStatus(targetStatus);

        for (OrderItem item : order.getOrderItems()) {
            item.setStatus(itemStatus);

            // Update related transaction if it exists
            transactionRepository.findByOrderItemOrderItemId(item.getOrderItemId()).ifPresent(transaction -> updateTransactionForItemStatus(transaction, itemStatus, notes, admin.getUsername()));

            orderItemRepository.save(item);
        }
    }

    // Log the admin action
    log.info("Admin {} force updated order {} from status {} to {}. Force update: {}",
            adminId, orderId, currentStatus, targetStatus, forceUpdate);

    // Load product images to avoid MultipleBagFetchException
    loadProductImagesForOrders(List.of(order));

    // Return updated order details
    return orderMapper.convertToDetailResponse(order);
}

/**
 * Maps an order status to the corresponding order item status
 */
    private OrderItemStatus mapOrderToItemStatus(OrderStatus orderStatus) {
        switch (orderStatus) {
            case PENDING:
                return OrderItemStatus.PENDING;
            case PROCESSING:
                return OrderItemStatus.PROCESSING;
            case OUT_FOR_DELIVERY:  // Keep this case for backward compatibility with OrderStatus enum
                return OrderItemStatus.OUT_FOR_DELIVERY;
            case DELIVERED:
                return OrderItemStatus.DELIVERED;
            case CANCELLED:
                return OrderItemStatus.CANCELLED;
            case REFUNDED:
                return OrderItemStatus.REFUNDED;
            default:
                return OrderItemStatus.PENDING;
        }
    }

    /**
     * Updates transaction data based on the new order item status
     */
    private void updateTransactionForItemStatus(Transaction transaction, OrderItemStatus status,
                                                String notes, String adminUsername) {
        switch (status) {
            case OUT_FOR_DELIVERY:
                transaction.setShippedAt(LocalDateTime.now());
                transaction.setStatus(TransactionStatus.SHIPPED); // Keep the TransactionStatus as SHIPPED for now
                break;

            case DELIVERED:
                transaction.setDeliveredAt(LocalDateTime.now());
                transaction.setStatus(TransactionStatus.DELIVERED);
                // Set escrow status to RELEASED after delivery
                transaction.setEscrowStatus(EscrowStatus.RELEASED);
                break;

            case CANCELLED:
                transaction.setStatus(TransactionStatus.CANCELLED);
                // Reset escrow status for cancelled transactions
                transaction.setEscrowStatus(EscrowStatus.REFUNDED);
                break;

            case REFUNDED:
                transaction.setStatus(TransactionStatus.REFUNDED);
                transaction.setEscrowStatus(EscrowStatus.REFUNDED);
                break;

            default:
                // No change for other statuses
                break;
        }
    }

    /**
     * Validates that the status transition is allowed according to business rules
     */
    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        // Define valid transitions
        Map<OrderStatus, List<OrderStatus>> validTransitions = Map.of(
                OrderStatus.PENDING, List.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED, OrderStatus.EXPIRED),
                OrderStatus.PROCESSING, List.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED, OrderStatus.REFUNDED),
                OrderStatus.OUT_FOR_DELIVERY, List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REFUNDED),

                OrderStatus.DELIVERED, List.of(OrderStatus.REFUNDED),
                OrderStatus.CANCELLED, List.of(),
                OrderStatus.REFUNDED, List.of()
        );

        // Check if transition is valid
        if (!validTransitions.getOrDefault(currentStatus, List.of()).contains(targetStatus) &&
                currentStatus != targetStatus) {
            throw new BusinessLogicException(
                    "Invalid status transition from " + currentStatus + " to " + targetStatus +
                            ". Use forceUpdate=true to bypass this check."
            );
        }
    }

    /**
     * Admin-only method to get all users with their order summary statistics
     *
     * @param search Optional search term to filter by username or email
     * @param accountStatus Optional filter for account status (active/inactive)
     * @param pageable Pagination and sorting parameters
     * @return Paginated list of users with their order statistics
     */
    public PagedResponse<Object> getAllUsersWithOrderSummary(String search, String accountStatus, Pageable pageable) {
        Page<User> usersPage;

        if (search != null && !search.isEmpty() && accountStatus != null && !accountStatus.isEmpty()) {
            // Filter by both search term and account status
            usersPage = userRepository.findByUsernameContainingOrEmailContainingAndAccountStatus(
                    search, search, accountStatus, pageable);
        } else if (search != null && !search.isEmpty()) {
            // Filter by search term only
            usersPage = userRepository.findByUsernameContainingOrEmailContaining(search, search, pageable);
        } else if (accountStatus != null && !accountStatus.isEmpty()) {
            // Filter by account status only
            usersPage = userRepository.findByAccountStatus(accountStatus, pageable);
        } else {
            // No filters
            usersPage = userRepository.findAll(pageable);
        }

        // Process each user to add order summary
        List<Object> userSummaries = usersPage.getContent().stream()
                .map(this::buildUserOrderSummary)
                .map(map -> (Object) map)
                .collect(Collectors.toList());

        // Create paged response with explicit Object type
        Page<Object> objectPage = new PageImpl<>(userSummaries, pageable, usersPage.getTotalElements());
        return PagedResponse.of(objectPage);
    }

    /**
     * Builds a summary of order statistics for a user
     */
    private Map<String, Object> buildUserOrderSummary(User user) {
        // Get all orders where user is buyer
        List<Order> buyerOrders = orderRepository.findByBuyerUserId(user.getUserId());

        // Count orders by status for this buyer
        Map<OrderStatus, Long> buyerStatusCounts = Arrays.stream(OrderStatus.values())
                .collect(Collectors.toMap(
                        status -> status,
                        status -> buyerOrders.stream().filter(o -> o.getStatus() == status).count(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        // Calculate total spent as buyer
        BigDecimal totalSpent = buyerOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PROCESSING ||
                        o.getStatus() == OrderStatus.OUT_FOR_DELIVERY ||
                        o.getStatus() == OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get all order items where user is seller
        List<OrderItem> sellerItems = orderItemRepository.findBySellerUserId(user.getUserId());

        // Count items by status for this seller
        Map<OrderItemStatus, Long> sellerStatusCounts = Arrays.stream(OrderItemStatus.values())
                .collect(Collectors.toMap(
                        status -> status,
                        status -> sellerItems.stream().filter(i -> i.getStatus() == status).count(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        // Calculate total earned as seller (minus platform fees)
        BigDecimal totalEarned = sellerItems.stream()
                .filter(i -> i.getStatus() == OrderItemStatus.PROCESSING ||
                        i.getStatus() == OrderItemStatus.OUT_FOR_DELIVERY ||
                        i.getStatus() == OrderItemStatus.DELIVERED)
                .map(i -> i.getPrice().subtract(i.getPlatformFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get date of last order (as buyer)
        LocalDateTime lastOrderDate = buyerOrders.stream()
                .map(Order::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        // Get date of last sale (as seller)
        LocalDateTime lastSaleDate = sellerItems.stream()
                .map(i -> i.getOrder().getCreatedAt())
                .max(LocalDateTime::compareTo)
                .orElse(null);

        // Build and return the summary
        Map<String, Object> summary = new LinkedHashMap<>();

        // User information
        summary.put("userId", user.getUserId());
        summary.put("username", user.getUsername());
        summary.put("email", user.getEmail());
        summary.put("firstName", user.getFirstName());
        summary.put("lastName", user.getLastName());
        summary.put("profilePictureUrl", user.getProfilePictureUrl());
        summary.put("accountStatus", user.getAccountStatus());
        summary.put("isVerified", user.getIsVerified());
        summary.put("joinedDate", user.getJoinedDate());
        summary.put("lastLoginAt", user.getLastLoginAt());

        // Buyer statistics
        Map<String, Object> buyerMap = new HashMap<>();
        buyerMap.put("totalOrders", buyerOrders.size());
        buyerMap.put("totalSpent", totalSpent);
        buyerMap.put("lastOrderDate", lastOrderDate);
        buyerMap.put("statusCounts", buyerStatusCounts);
        summary.put("buyer", buyerMap);

        // Seller statistics
        Map<String, Object> sellerMap = new HashMap<>();
        sellerMap.put("totalItems", sellerItems.size());
        sellerMap.put("totalEarned", totalEarned);
        sellerMap.put("lastSaleDate", lastSaleDate);
        sellerMap.put("statusCounts", sellerStatusCounts);
        sellerMap.put("rating", user.getSellerRating());
        sellerMap.put("reviewsCount", user.getSellerReviewsCount());
        summary.put("seller", sellerMap);

        return summary;
    }

    /**
     * Parse a date string in ISO format to LocalDateTime
     * Supports both date-only strings (yyyy-MM-dd) and full datetime strings (yyyy-MM-ddTHH:mm:ss)
     *
     * @param dateStr Date string in ISO format
     * @return Parsed LocalDateTime object
     */
    public LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        try {
            // Check if it's date-only format
            if (!dateStr.contains("T")) {
                // If date only, set to beginning of day
                return LocalDate.parse(dateStr).atStartOfDay();
            }

            // Otherwise parse as full datetime
            return LocalDateTime.parse(dateStr);
        } catch (DateTimeParseException e) {
            log.error("Failed to parse date string: {}", dateStr);
            throw new BusinessLogicException("Invalid date format: " + dateStr +
                    ". Expected format: yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss");
        }
    }

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

    @Transactional
    public OrderDetailResponse requestItemPickup(Long orderId, Long itemId, Long userId) {
        Order order = getOrderWithValidation(orderId, userId, false);
        OrderItem orderItem = findOrderItem(order, itemId);

        // Validate that the user making the request is the seller of the specified OrderItem
        if (!orderItem.getSeller().getUserId().equals(userId)) {
            throw new UnauthorizedException("Only the seller can request pickup for this item");
        }

        // Validate that the OrderItem's current status is PROCESSING
        if (orderItem.getStatus() != OrderItemStatus.PROCESSING) {
            throw new BusinessLogicException("Item must be in PROCESSING status to request pickup. Current status: " + orderItem.getStatus());
        }

        // Change the OrderItemStatus to AWAITING_PICKUP
        orderItem.setStatus(OrderItemStatus.AWAITING_PICKUP);
        
        updateOverallOrderStatus(order);
        order = orderRepository.save(order);

        return convertToDetailResponse(order);
    }
}
