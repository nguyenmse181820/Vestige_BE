package se.vestige_be.service;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.time.LocalDateTime;
import java.util.*;
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

    @Transactional
    public OrderDetailResponse createMultiProductOrder(OrderCreateRequest request, Long buyerId) {
        // Validate buyer
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found"));

        // Validate shipping address
        UserAddress shippingAddress = userAddressRepository.findById(request.getShippingAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Shipping address not found"));

        if (!shippingAddress.getUser().getUserId().equals(buyerId)) {
            throw new UnauthorizedException("Shipping address does not belong to buyer");
        }

        // Validate and process each item
        List<OrderItemData> orderItemsData = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalShippingFee = BigDecimal.ZERO;

        for (OrderCreateRequest.OrderItemRequest itemRequest : request.getItems()) {
            OrderItemData itemData = validateAndProcessItem(itemRequest, buyerId);
            orderItemsData.add(itemData);
            totalAmount = totalAmount.add(itemData.getItemPrice());
        }

        totalAmount = totalAmount.add(totalShippingFee);

        // Create Order
        Order order = Order.builder()
                .buyer(buyer)
                .totalAmount(totalAmount)
                .shippingAddress(shippingAddress)
                .status(OrderStatus.PENDING)
                .build();

        order = orderRepository.save(order);
        log.info("Created order: {} with total amount: {} VND", order.getOrderId(), totalAmount);

        // Create OrderItems and Transactions
        for (OrderItemData itemData : orderItemsData) {
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(itemData.getProduct())
                    .seller(itemData.getProduct().getSeller())
                    .price(itemData.getItemPrice())
                    .platformFee(itemData.getPlatformFee())
                    .feePercentage(itemData.getFeePercentage())
                    .status(OrderItemStatus.PENDING)
                    .escrowStatus(EscrowStatus.HOLDING)
                    .build();

            order.getOrderItems().add(orderItem);

            // Create Transaction for each item
            Transaction transaction = Transaction.builder()
                    .orderItem(orderItem)
                    .seller(itemData.getProduct().getSeller())
                    .buyer(buyer)
                    .offer(itemData.getOffer())
                    .amount(itemData.getItemPrice())
                    .platformFee(itemData.getPlatformFee())
                    .feePercentage(itemData.getFeePercentage())
                    .shippingAddress(shippingAddress)
                    .status(TransactionStatus.PENDING)
                    .escrowStatus(EscrowStatus.HOLDING)
                    .buyerProtectionEligible(true)
                    .build();

            transactionRepository.save(transaction);

            // Mark product as sold
            itemData.getProduct().setStatus(ProductStatus.SOLD);
            itemData.getProduct().setSoldAt(LocalDateTime.now());
            productRepository.save(itemData.getProduct());
        }

        order = orderRepository.save(order);
        log.info("Order created successfully: {} with {} items", order.getOrderId(), order.getOrderItems().size());

        return convertToDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse confirmPayment(Long orderId, Long buyerId) {
        log.info("Confirming payment for order: orderId={}, buyerId={}", orderId, buyerId);

        Order order = getOrderWithValidation(orderId, buyerId, true);

        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new BusinessLogicException("Order is not in pending status");
        }

        // Update order status
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());

        // Update all order items to process
        order.getOrderItems().forEach(item -> {
            if (item.getStatus() == OrderItemStatus.PENDING) {
                item.setStatus(OrderItemStatus.PROCESSING);
            }
        });

        order = orderRepository.save(order);
        log.info("Payment confirmed for order: {}", orderId);

        return convertToDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse updateOrderItemStatus(Long orderId, Long itemId, OrderStatusUpdateRequest request, Long userId) {
        log.info("Updating order item status: orderId={}, itemId={}, newStatus={}, userId={}",
                orderId, itemId, request.getStatus(), userId);

        Order order = getOrderWithValidation(orderId, userId, false);
        OrderItem orderItem = order.getOrderItems().stream()
                .filter(item -> item.getOrderItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found"));

        OrderItemStatus newStatus;
        try {
            newStatus = OrderItemStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessLogicException("Invalid order item status: " + request.getStatus());
        }

        boolean isBuyer = order.getBuyer().getUserId().equals(userId);
        boolean isSeller = orderItem.getSeller().getUserId().equals(userId);

        // Validate status transition permissions
        validateItemStatusUpdate(orderItem.getStatus(), newStatus, isBuyer, isSeller);

        // Update item status
        orderItem.setStatus(newStatus);

        // Update transaction if exists
        Optional<Transaction> transactionOpt = transactionRepository.findByOrderItemOrderItemId(itemId);

        switch (newStatus) {
            case SHIPPED:
                if (transactionOpt.isPresent()) {
                    Transaction trans = transactionOpt.get();
                    trans.setShippedAt(LocalDateTime.now());
                    trans.setTrackingNumber(request.getTrackingNumber());
                    trans.setTrackingUrl(request.getTrackingUrl());
                    trans.setStatus(TransactionStatus.SHIPPED);
                    transactionRepository.save(trans);
                }
                log.info("Order item {} shipped with tracking: {}", itemId, request.getTrackingNumber());
                break;

            case DELIVERED:
                orderItem.setEscrowStatus(EscrowStatus.RELEASED);
                if (transactionOpt.isPresent()) {
                    Transaction trans = transactionOpt.get();
                    trans.setDeliveredAt(LocalDateTime.now());
                    trans.setStatus(TransactionStatus.DELIVERED);
                    transactionRepository.save(trans);
                }
                log.info("Order item {} delivered, escrow released", itemId);
                break;

            case CANCELLED:
                orderItem.setEscrowStatus(EscrowStatus.REFUNDED);
                orderItem.getProduct().setStatus(ProductStatus.ACTIVE);
                orderItem.getProduct().setSoldAt(null);
                productRepository.save(orderItem.getProduct());

                if (transactionOpt.isPresent()) {
                    Transaction trans = transactionOpt.get();
                    trans.setStatus(TransactionStatus.CANCELLED);
                    transactionRepository.save(trans);
                }
                log.info("Order item {} cancelled, escrow refunded", itemId);
                break;
        }

        // Update overall order status based on item statuses
        updateOverallOrderStatus(order);
        order = orderRepository.save(order);

        return convertToDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse shipOrderItem(Long orderId, Long itemId, OrderStatusUpdateRequest request, Long sellerId) {
        log.info("Shipping order item: orderId={}, itemId={}, sellerId={}", orderId, itemId, sellerId);

        OrderStatusUpdateRequest shipRequest = OrderStatusUpdateRequest.builder()
                .status("SHIPPED")
                .notes(request.getNotes())
                .trackingNumber(request.getTrackingNumber())
                .trackingUrl(request.getTrackingUrl())
                .build();

        return updateOrderItemStatus(orderId, itemId, shipRequest, sellerId);
    }

    @Transactional
    public OrderDetailResponse confirmItemDelivery(Long orderId, Long itemId, String notes, Long buyerId) {
        log.info("Confirming item delivery: orderId={}, itemId={}, buyerId={}", orderId, itemId, buyerId);

        OrderStatusUpdateRequest deliveryRequest = OrderStatusUpdateRequest.builder()
                .status("DELIVERED")
                .notes(notes)
                .build();

        return updateOrderItemStatus(orderId, itemId, deliveryRequest, buyerId);
    }

    @Transactional
    public OrderDetailResponse cancelOrderItem(Long orderId, Long itemId, String reason, Long userId) {
        log.info("Cancelling order item: orderId={}, itemId={}, userId={}, reason={}", orderId, itemId, userId, reason);

        Order order = getOrderWithValidation(orderId, userId, false);
        OrderItem orderItem = order.getOrderItems().stream()
                .filter(item -> item.getOrderItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found"));

        // Check if user can cancel this item
        boolean isBuyer = order.getBuyer().getUserId().equals(userId);
        boolean isSeller = orderItem.getSeller().getUserId().equals(userId);

        if (!isBuyer && !isSeller) {
            throw new UnauthorizedException("Not authorized to cancel this item");
        }

        // Check if item can be cancelled based on status
        if (!isItemCancellable(orderItem.getStatus())) {
            throw new BusinessLogicException(
                    "Cannot cancel item in '" + orderItem.getStatus() + "' status. " +
                            "Only PENDING and PROCESSING items can be cancelled."
            );
        }

        // Perform cancellation
        cancelOrderItemInternal(orderItem, reason);

        // Auto-update overall order status
        updateOverallOrderStatus(order);
        order = orderRepository.save(order);

        return convertToDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse cancelOrder(Long orderId, String reason, Long userId) {
        log.info("Cancelling order: orderId={}, userId={}, reason={}", orderId, userId, reason);

        Order order = getOrderWithValidation(orderId, userId, false);

        boolean hasShippedItems = order.getOrderItems().stream()
                .anyMatch(item -> item.getStatus() == OrderItemStatus.SHIPPED ||
                        item.getStatus() == OrderItemStatus.DELIVERED);

        if (hasShippedItems) {
            throw new BusinessLogicException(
                    "Cannot cancel order with shipped items. " +
                            "Please cancel individual items or contact support for assistance."
            );
        }

        // Cancel all cancellable items
        List<OrderItem> cancelledItems = new ArrayList<>();
        List<OrderItem> unCancellableItems = new ArrayList<>();

        for (OrderItem item : order.getOrderItems()) {
            if (isItemCancellable(item.getStatus())) {
                cancelOrderItemInternal(item, reason);
                cancelledItems.add(item);
            } else {
                unCancellableItems.add(item);
            }
        }

        if (cancelledItems.isEmpty()) {
            throw new BusinessLogicException("No items can be cancelled in this order");
        }

        // Auto-update overall order status
        updateOverallOrderStatus(order);
        order = orderRepository.save(order);

        log.info("Order {} cancelled: {} items cancelled, {} items unchanged",
                orderId, cancelledItems.size(), unCancellableItems.size());

        return convertToDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse updateOrderStatus(Long orderId, OrderStatusUpdateRequest request, Long userId) {
        log.info("Updating order status: orderId={}, newStatus={}, userId={}", orderId, request.getStatus(), userId);

        Order order = getOrderWithValidation(orderId, userId, false);

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessLogicException("Invalid order status: " + request.getStatus());
        }

        boolean isBuyer = order.getBuyer().getUserId().equals(userId);

        // Only allow specific manual transitions
        switch (newStatus) {
            case PAID:
                // Only buyer can confirm payment
                if (!isBuyer || !OrderStatus.PENDING.equals(order.getStatus())) {
                    throw new BusinessLogicException("Only pending orders can be marked as paid by buyer");
                }
                order.setStatus(OrderStatus.PAID);
                order.setPaidAt(LocalDateTime.now());

                // Update all pending items to processing
                order.getOrderItems().forEach(item -> {
                    if (item.getStatus() == OrderItemStatus.PENDING) {
                        item.setStatus(OrderItemStatus.PROCESSING);
                    }
                });
                break;

            case CANCELLED:
                // Both buyer and seller can cancel (with restrictions)
                if (!isBuyer && !isSellerInOrder(order, userId)) {
                    throw new BusinessLogicException("Not authorized to cancel this order");
                }

                // Cancel all cancellable items
                order.getOrderItems().forEach(item -> {
                    if (item.getStatus() == OrderItemStatus.PENDING ||
                            item.getStatus() == OrderItemStatus.PROCESSING) {
                        cancelOrderItemInternal(item, request.getNotes());
                    }
                });
                break;

            default:
                throw new BusinessLogicException("Order status '" + newStatus +
                        "' updates automatically based on item statuses. Use item-specific endpoints instead.");
        }

        // Auto-update overall status
        updateOverallOrderStatus(order);
        order = orderRepository.save(order);

        return convertToDetailResponse(order);
    }

    public PagedResponse<OrderListResponse> getUserOrders(Long userId, String status, String role, Pageable pageable) {
        log.debug("Getting user orders: userId={}, status={}, role={}", userId, status, role);

        Page<Order> orders;

        if ("seller".equals(role)) {
            // For sellers, get orders that contain their items
            Page<OrderItem> sellerItems;

            if (status != null) {
                try {
                    OrderItemStatus itemStatus = OrderItemStatus.valueOf(status.toUpperCase());
                    sellerItems = orderItemRepository.findBySellerUserIdAndStatusOrderByOrderCreatedAtDesc(
                            userId, itemStatus, pageable);
                } catch (IllegalArgumentException e) {
                    // If status is not an OrderItemStatus, get all items
                    sellerItems = orderItemRepository.findBySellerUserIdOrderByOrderCreatedAtDesc(userId, pageable);
                }
            } else {
                sellerItems = orderItemRepository.findBySellerUserIdOrderByOrderCreatedAtDesc(userId, pageable);
            }

            // Extract unique orders from order items
            List<Order> uniqueOrders = sellerItems.getContent().stream()
                    .map(OrderItem::getOrder)
                    .distinct()
                    .collect(Collectors.toList());

            orders = new PageImpl<>(uniqueOrders, pageable, sellerItems.getTotalElements());

        } else {
            // For buyers, use direct order queries
            if (status != null) {
                try {
                    OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
                    orders = orderRepository.findByBuyerUserIdAndStatusOrderByCreatedAtDesc(
                            userId, orderStatus, pageable);
                } catch (IllegalArgumentException e) {
                    orders = orderRepository.findByBuyerUserIdOrderByCreatedAtDesc(userId, pageable);
                }
            } else {
                orders = orderRepository.findByBuyerUserIdOrderByCreatedAtDesc(userId, pageable);
            }
        }

        Page<OrderListResponse> orderResponses = orders.map(this::convertToListResponse);
        return PagedResponse.of(orderResponses);
    }

    public OrderDetailResponse getOrderById(Long orderId, Long userId) {
        log.debug("Getting order details: orderId={}, userId={}", orderId, userId);

        Order order = getOrderWithValidation(orderId, userId, false);
        return convertToDetailResponse(order);
    }

    public OrderSellersResponse getOrderSellers(Long orderId, Long userId) {
        log.debug("Getting order sellers: orderId={}, userId={}", orderId, userId);

        Order order = getOrderWithValidation(orderId, userId, false);

        Map<Long, List<OrderDetailResponse.OrderItemDetail>> sellerItems = new HashMap<>();
        List<OrderSellersResponse.SellerSummary> sellerSummaries = new ArrayList<>();

        Map<Long, List<OrderItem>> itemsBySeller = order.getOrderItems().stream()
                .collect(Collectors.groupingBy(item -> item.getSeller().getUserId()));

        for (Map.Entry<Long, List<OrderItem>> entry : itemsBySeller.entrySet()) {
            Long sellerId = entry.getKey();
            List<OrderItem> items = entry.getValue();
            OrderItem firstItem = items.getFirst();

            // Convert items to detail format
            List<OrderDetailResponse.OrderItemDetail> itemDetails = items.stream()
                    .map(this::convertToItemDetail)
                    .collect(Collectors.toList());

            sellerItems.put(sellerId, itemDetails);

            // Determine overall status for this seller
            Set<OrderItemStatus> statuses = items.stream()
                    .map(OrderItem::getStatus)
                    .collect(Collectors.toSet());

            String overallStatus = statuses.size() == 1 ?
                    statuses.iterator().next().name() : "MIXED";

            // Create seller summary
            OrderSellersResponse.SellerSummary summary = OrderSellersResponse.SellerSummary.builder()
                    .sellerId(sellerId)
                    .sellerUsername(firstItem.getSeller().getUsername())
                    .sellerName(firstItem.getSeller().getFirstName() + " " + firstItem.getSeller().getLastName())
                    .isLegitProfile(firstItem.getSeller().getIsLegitProfile())
                    .itemCount(items.size())
                    .overallStatus(overallStatus)
                    .build();

            sellerSummaries.add(summary);
        }

        return OrderSellersResponse.builder()
                .sellerCount(sellerItems.size())
                .itemsBySeller(sellerItems)
                .sellerSummaries(sellerSummaries)
                .build();
    }

    private OrderItemData validateAndProcessItem(OrderCreateRequest.OrderItemRequest itemRequest, Long buyerId) {
        Product product = productRepository.findById(itemRequest.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.getProductId()));

        if (!ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new BusinessLogicException("Product is not available: " + product.getTitle());
        }

        if (product.getSeller().getUserId().equals(buyerId)) {
            throw new BusinessLogicException("Cannot purchase your own product: " + product.getTitle());
        }

        // Get price (from offer or product)
        BigDecimal itemPrice;
        Offer offer = null;

        if (itemRequest.getOfferId() != null) {
            offer = offerRepository.findById(itemRequest.getOfferId())
                    .orElseThrow(() -> new ResourceNotFoundException("Offer not found"));

            if (!OfferStatus.ACCEPTED.equals(offer.getStatus())) {
                throw new BusinessLogicException("Offer is not accepted for product: " + product.getTitle());
            }

            if (!offer.getBuyer().getUserId().equals(buyerId)) {
                throw new UnauthorizedException("Offer does not belong to buyer");
            }

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

    private void updateOverallOrderStatus(Order order) {
        List<OrderItemStatus> itemStatuses = order.getOrderItems().stream()
                .map(OrderItem::getStatus)
                .toList();

        // Count statuses
        long totalItems = itemStatuses.size();
        long deliveredItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.DELIVERED).count();
        long shippedItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.SHIPPED).count();
        long processingItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.PROCESSING).count();
        long cancelledItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.CANCELLED).count();
        long pendingItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.PENDING).count();

        // Auto-update order status based on item statuses
        if (deliveredItems == totalItems) {
            order.setStatus(OrderStatus.DELIVERED);
        } else if (cancelledItems == totalItems) {
            order.setStatus(OrderStatus.CANCELLED);
        } else if (shippedItems + deliveredItems > 0) {
            order.setStatus(OrderStatus.SHIPPED);
        } else if (processingItems > 0 && pendingItems == 0) {
            order.setStatus(OrderStatus.PAID);
        } else if (pendingItems > 0) {
            order.setStatus(OrderStatus.PENDING);
        }
    }

    private void validateItemStatusUpdate(OrderItemStatus currentStatus, OrderItemStatus newStatus, boolean isBuyer, boolean isSeller) {
        switch (newStatus) {
            case PROCESSING:
                if (!isBuyer || !OrderItemStatus.PENDING.equals(currentStatus)) {
                    throw new BusinessLogicException("Invalid status transition from " + currentStatus + " to " + newStatus);
                }
                break;
            case SHIPPED:
                if (!isSeller || !OrderItemStatus.PROCESSING.equals(currentStatus)) {
                    throw new BusinessLogicException("Only seller can mark item as shipped from PROCESSING status");
                }
                break;
            case DELIVERED:
                if (!isBuyer || !OrderItemStatus.SHIPPED.equals(currentStatus)) {
                    throw new BusinessLogicException("Only buyer can confirm delivery from SHIPPED status");
                }
                break;
            case CANCELLED:
                if (!OrderItemStatus.PENDING.equals(currentStatus) && !OrderItemStatus.PROCESSING.equals(currentStatus)) {
                    throw new BusinessLogicException("Item can only be cancelled from PENDING or PROCESSING status");
                }
                break;
            default:
                throw new BusinessLogicException("Invalid status: " + newStatus);
        }
    }

    private boolean isItemCancellable(OrderItemStatus status) {
        return status == OrderItemStatus.PENDING || status == OrderItemStatus.PROCESSING;
    }

    private void cancelOrderItemInternal(OrderItem orderItem, String reason) {
        // Update item status
        orderItem.setStatus(OrderItemStatus.CANCELLED);
        orderItem.setEscrowStatus(EscrowStatus.REFUNDED);

        // Mark product as available again
        orderItem.getProduct().setStatus(ProductStatus.ACTIVE);
        orderItem.getProduct().setSoldAt(null);
        productRepository.save(orderItem.getProduct());

        // Update transaction
        Optional<Transaction> transactionOpt = transactionRepository.findByOrderItemOrderItemId(orderItem.getOrderItemId());
        if (transactionOpt.isPresent()) {
            Transaction trans = transactionOpt.get();
            trans.setStatus(TransactionStatus.CANCELLED);
            transactionRepository.save(trans);
        }
    }

    private boolean isSellerInOrder(Order order, Long userId) {
        return order.getOrderItems().stream()
                .anyMatch(item -> item.getSeller().getUserId().equals(userId));
    }

    private Order getOrderWithValidation(Long orderId, Long userId, boolean buyerOnly) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        boolean isBuyer = order.getBuyer().getUserId().equals(userId);
        boolean isSeller = order.getOrderItems().stream()
                .anyMatch(item -> item.getSeller().getUserId().equals(userId));

        if (buyerOnly && !isBuyer) {
            throw new UnauthorizedException("Not authorized to access this order");
        }

        if (!buyerOnly && !isBuyer && !isSeller) {
            throw new UnauthorizedException("Not authorized to access this order");
        }

        return order;
    }

    private OrderListResponse convertToListResponse(Order order) {
        List<OrderListResponse.OrderItemSummary> itemSummaries = order.getOrderItems().stream()
                .limit(3) // Show first 3 items for preview
                .map(this::convertToItemSummary)
                .collect(Collectors.toList());

        Set<Long> uniqueSellerIds = order.getOrderItems().stream()
                .map(item -> item.getSeller().getUserId())
                .collect(Collectors.toSet());

        // Determine overall escrow status
        String overallEscrowStatus = "MIXED";
        Set<EscrowStatus> escrowStatuses = order.getOrderItems().stream()
                .map(OrderItem::getEscrowStatus)
                .collect(Collectors.toSet());

        if (escrowStatuses.size() == 1) {
            overallEscrowStatus = escrowStatuses.iterator().next().name();
        }

        return OrderListResponse.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .totalItems(order.getOrderItems().size())
                .uniqueSellers(uniqueSellerIds.size())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .itemSummaries(itemSummaries)
                .overallEscrowStatus(overallEscrowStatus)
                .totalPlatformFee(order.getOrderItems().stream()
                        .map(OrderItem::getPlatformFee)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }

    private OrderListResponse.OrderItemSummary convertToItemSummary(OrderItem item) {
        String primaryImageUrl = null;
        if (item.getProduct().getImages() != null && !item.getProduct().getImages().isEmpty()) {
            primaryImageUrl = item.getProduct().getImages().stream()
                    .filter(ProductImage::getIsPrimary)
                    .map(ProductImage::getImageUrl)
                    .findFirst()
                    .orElse(item.getProduct().getImages().getFirst().getImageUrl());
        }

        return OrderListResponse.OrderItemSummary.builder()
                .productId(item.getProduct().getProductId())
                .productTitle(item.getProduct().getTitle())
                .productImage(primaryImageUrl)
                .price(item.getPrice())
                .sellerUsername(item.getSeller().getUsername())
                .sellerIsLegitProfile(item.getSeller().getIsLegitProfile())
                .escrowStatus(item.getEscrowStatus().name())
                .itemStatus(item.getStatus().name())
                .build();
    }

    private OrderDetailResponse convertToDetailResponse(Order order) {
        List<OrderDetailResponse.OrderItemDetail> orderItemDetails = order.getOrderItems().stream()
                .map(this::convertToItemDetail)
                .collect(Collectors.toList());

        // Group items by seller
        Map<String, List<OrderDetailResponse.OrderItemDetail>> itemsBySeller = orderItemDetails.stream()
                .collect(Collectors.groupingBy(item -> item.getSeller().getUsername()));

        BigDecimal totalPlatformFee = order.getOrderItems().stream()
                .map(OrderItem::getPlatformFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<Long> uniqueSellerIds = order.getOrderItems().stream()
                .map(item -> item.getSeller().getUserId())
                .collect(Collectors.toSet());

        // Build escrow summary
        OrderDetailResponse.OrderEscrowSummary escrowSummary = buildEscrowSummary(order.getOrderItems());

        // Build shipping address
        OrderDetailResponse.ShippingAddressInfo shippingAddressInfo = OrderDetailResponse.ShippingAddressInfo.builder()
                .addressId(order.getShippingAddress().getAddressId())
                .addressLine1(order.getShippingAddress().getAddressLine1())
                .addressLine2(order.getShippingAddress().getAddressLine2())
                .city(order.getShippingAddress().getCity())
                .state(order.getShippingAddress().getState())
                .postalCode(order.getShippingAddress().getPostalCode())
                .country(order.getShippingAddress().getCountry())
                .build();

        return OrderDetailResponse.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .totalPlatformFee(totalPlatformFee)
                .totalItems(order.getOrderItems().size())
                .uniqueSellers(uniqueSellerIds.size())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .orderItems(orderItemDetails)
                .shippingAddress(shippingAddressInfo)
                .escrowSummary(escrowSummary)
                .itemsBySeller(itemsBySeller)
                .build();
    }

    private OrderDetailResponse.OrderItemDetail convertToItemDetail(OrderItem item) {
        Product product = item.getProduct();
        String primaryImageUrl = null;
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            primaryImageUrl = product.getImages().stream()
                    .filter(ProductImage::getIsPrimary)
                    .map(ProductImage::getImageUrl)
                    .findFirst()
                    .orElse(product.getImages().getFirst().getImageUrl());
        }

        OrderDetailResponse.ProductInfo productInfo = OrderDetailResponse.ProductInfo.builder()
                .productId(product.getProductId())
                .title(product.getTitle())
                .description(product.getDescription())
                .condition(product.getCondition().name())
                .size(product.getSize())
                .color(product.getColor())
                .primaryImageUrl(primaryImageUrl)
                .categoryId(product.getCategory().getCategoryId())
                .categoryName(product.getCategory().getName())
                .brandId(product.getBrand().getBrandId())
                .brandName(product.getBrand().getName())
                .build();

        OrderDetailResponse.SellerInfo sellerInfo = OrderDetailResponse.SellerInfo.builder()
                .userId(item.getSeller().getUserId())
                .username(item.getSeller().getUsername())
                .firstName(item.getSeller().getFirstName())
                .lastName(item.getSeller().getLastName())
                .isLegitProfile(item.getSeller().getIsLegitProfile())
                .sellerRating(item.getSeller().getSellerRating())
                .sellerReviewsCount(item.getSeller().getSellerReviewsCount())
                .build();

        // Get transaction info if available
        OrderDetailResponse.TransactionInfo transactionInfo = null;
        Optional<Transaction> transactionOpt = transactionRepository.findByOrderItemOrderItemId(item.getOrderItemId());
        if (transactionOpt.isPresent()) {
            Transaction trans = transactionOpt.get();
            transactionInfo = OrderDetailResponse.TransactionInfo.builder()
                    .transactionId(trans.getTransactionId())
                    .trackingNumber(trans.getTrackingNumber())
                    .trackingUrl(trans.getTrackingUrl())
                    .shippedAt(trans.getShippedAt())
                    .deliveredAt(trans.getDeliveredAt())
                    .buyerProtectionEligible(trans.getBuyerProtectionEligible())
                    .build();
        }

        return OrderDetailResponse.OrderItemDetail.builder()
                .orderItemId(item.getOrderItemId())
                .price(item.getPrice())
                .platformFee(item.getPlatformFee())
                .feePercentage(item.getFeePercentage())
                .status(item.getStatus().name())
                .escrowStatus(item.getEscrowStatus().name())
                .product(productInfo)
                .seller(sellerInfo)
                .transaction(transactionInfo)
                .build();
    }

    private OrderDetailResponse.OrderEscrowSummary buildEscrowSummary(List<OrderItem> orderItems) {
        BigDecimal totalEscrowAmount = orderItems.stream()
                .map(OrderItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPlatformFee = orderItems.stream()
                .map(OrderItem::getPlatformFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSellerAmount = totalEscrowAmount.subtract(totalPlatformFee);

        Map<EscrowStatus, Long> statusCounts = orderItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getEscrowStatus, Collectors.counting()));

        // Group by seller for escrow breakdown
        Map<Long, List<OrderItem>> itemsBySeller = orderItems.stream()
                .collect(Collectors.groupingBy(item -> item.getSeller().getUserId()));

        List<OrderDetailResponse.SellerEscrowInfo> sellerEscrows = itemsBySeller.values().stream()
                .map(sellerItems -> {
                    OrderItem firstItem = sellerItems.getFirst();

                    BigDecimal sellerEscrowAmount = sellerItems.stream()
                            .map(OrderItem::getPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal sellerPlatformFee = sellerItems.stream()
                            .map(OrderItem::getPlatformFee)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Determine overall escrow status for this seller
                    Set<EscrowStatus> sellerEscrowStatuses = sellerItems.stream()
                            .map(OrderItem::getEscrowStatus)
                            .collect(Collectors.toSet());

                    String escrowStatus = sellerEscrowStatuses.size() == 1 ?
                            sellerEscrowStatuses.iterator().next().name() : "MIXED";

                    return OrderDetailResponse.SellerEscrowInfo.builder()
                            .sellerId(firstItem.getSeller().getUserId())
                            .sellerUsername(firstItem.getSeller().getUsername())
                            .escrowAmount(sellerEscrowAmount)
                            .platformFee(sellerPlatformFee)
                            .sellerAmount(sellerEscrowAmount.subtract(sellerPlatformFee))
                            .escrowStatus(escrowStatus)
                            .itemCount(sellerItems.size())
                            .build();
                })
                .collect(Collectors.toList());

        return OrderDetailResponse.OrderEscrowSummary.builder()
                .totalEscrowAmount(totalEscrowAmount)
                .totalPlatformFee(totalPlatformFee)
                .totalSellerAmount(totalSellerAmount)
                .itemsInEscrow(statusCounts.getOrDefault(EscrowStatus.HOLDING, 0L).intValue())
                .itemsReleased(statusCounts.getOrDefault(EscrowStatus.RELEASED, 0L).intValue())
                .itemsRefunded(statusCounts.getOrDefault(EscrowStatus.REFUNDED, 0L).intValue())
                .sellerEscrows(sellerEscrows)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class OrderItemData {
        private Product product;
        private Offer offer;
        private BigDecimal itemPrice;
        private BigDecimal platformFee;
        private BigDecimal feePercentage;
        private String notes;
    }
}