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
    public Object createOrder(OrderCreateRequest request, Long buyerId) {
        if (request.getPaymentMethod() == PaymentMethod.COD) {
            return createCodOrder(request, buyerId);
//        } else if (request.getPaymentMethod() == PaymentMethod.ONLINE) {
//            return prepareOnlinePayment(request, buyerId);
        } else {
            throw new BusinessLogicException("Unsupported payment method.");
        }
    }

    private OrderDetailResponse createCodOrder(OrderCreateRequest request, Long buyerId) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found"));

        UserAddress shippingAddress = userAddressRepository.findById(request.getShippingAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Shipping address not found"));

        if (!shippingAddress.getUser().getUserId().equals(buyerId)) {
            throw new UnauthorizedException("Shipping address does not belong to buyer");
        }

        List<OrderItemData> orderItemsData = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderCreateRequest.OrderItemRequest itemRequest : request.getItems()) {
            OrderItemData itemData = validateAndProcessItem(itemRequest, buyerId);
            orderItemsData.add(itemData);
            totalAmount = totalAmount.add(itemData.getItemPrice());
        }

        Order order = Order.builder()
                .buyer(buyer)
                .totalAmount(totalAmount)
                .shippingAddress(shippingAddress)
                .status(OrderStatus.PROCESSING)
                .paymentMethod(PaymentMethod.COD)
                .build();
        order = orderRepository.save(order);

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


            Product product = itemData.getProduct();
            product.setStatus(ProductStatus.SOLD);
            product.setSoldAt(LocalDateTime.now());
            productRepository.save(product);
        }

        order = orderRepository.save(order);
        return convertToDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse createMultiProductOrder(OrderCreateRequest request, Long buyerId) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found"));

        UserAddress shippingAddress = userAddressRepository.findById(request.getShippingAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Shipping address not found"));

        if (!shippingAddress.getUser().getUserId().equals(buyerId)) {
            throw new UnauthorizedException("Shipping address does not belong to buyer");
        }

        List<OrderItemData> orderItemsData = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        // BigDecimal totalShippingFee = BigDecimal.ZERO;

        for (OrderCreateRequest.OrderItemRequest itemRequest : request.getItems()) {
            OrderItemData itemData = validateAndProcessItem(itemRequest, buyerId);
            orderItemsData.add(itemData);
            totalAmount = totalAmount.add(itemData.getItemPrice());
        }

        // totalAmount = totalAmount.add(totalShippingFee);

        Order order = Order.builder()
                .buyer(buyer)
                .totalAmount(totalAmount)
                .shippingAddress(shippingAddress)
                .status(OrderStatus.PENDING)
                .build();

        order = orderRepository.save(order);

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


            Product product = itemData.getProduct();
            product.setStatus(ProductStatus.SOLD);
            product.setSoldAt(LocalDateTime.now());
            productRepository.save(product);
        }

        order = orderRepository.save(order);

        return convertToDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse confirmPayment(Long orderId, Long buyerId) {
        Order order = getOrderWithValidation(orderId, buyerId, true);

        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new BusinessLogicException("Order is not in pending status");
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());

        order.getOrderItems().forEach(item -> {
            if (item.getStatus() == OrderItemStatus.PENDING) {
                item.setStatus(OrderItemStatus.PROCESSING);
            }
        });

        order = orderRepository.save(order);

        return convertToDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse updateOrderItemStatus(Long orderId, Long itemId, OrderStatusUpdateRequest request, Long userId) {
        Order order = getOrderWithValidation(orderId, userId, false);
        OrderItem orderItem = order.getOrderItems().stream()
                .filter(item -> item.getOrderItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with ID: " + itemId + " in order: " + orderId));

        OrderItemStatus newStatus;
        try {
            newStatus = OrderItemStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessLogicException("Invalid order item status: " + request.getStatus());
        }

        boolean isBuyer = order.getBuyer().getUserId().equals(userId);
        boolean isSeller = orderItem.getSeller().getUserId().equals(userId);

        validateItemStatusUpdate(orderItem.getStatus(), newStatus, isBuyer, isSeller);

        Transaction transaction = null;
        if (newStatus == OrderItemStatus.SHIPPED || newStatus == OrderItemStatus.DELIVERED || newStatus == OrderItemStatus.CANCELLED) {
            transaction = transactionRepository.findByOrderItemOrderItemId(itemId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Transaction record not found for order item ID: " + itemId +
                                    ", which is required for status update to " + newStatus));
        }

        orderItem.setStatus(newStatus); // Set new status on the item

        switch (newStatus) {
            case SHIPPED:
                // Edge Case 1: Missing Tracking Information for "SHIPPED" status
                if (request.getTrackingNumber() == null || request.getTrackingNumber().isBlank()) {
                    throw new BusinessLogicException("Tracking number is required when marking an item as SHIPPED.");
                }
                // transaction will not be null here due to the check above
                transaction.setShippedAt(LocalDateTime.now());
                transaction.setTrackingNumber(request.getTrackingNumber());
                transaction.setTrackingUrl(request.getTrackingUrl()); // trackingUrl can be optional
                transaction.setStatus(TransactionStatus.SHIPPED);
                transactionRepository.save(transaction);
                log.info("Order item {} shipped with tracking: {}", itemId, request.getTrackingNumber());
                break;

            case DELIVERED:
                orderItem.setEscrowStatus(EscrowStatus.RELEASED);
                // transaction will not be null here
                transaction.setDeliveredAt(LocalDateTime.now());
                transaction.setStatus(TransactionStatus.DELIVERED);
                transactionRepository.save(transaction);
                log.info("Order item {} delivered, escrow released", itemId);
                break;

            case CANCELLED:
                orderItem.setEscrowStatus(EscrowStatus.REFUNDED);

                // Edge Case 3: Product State Inconsistency on Cancellation
                Product productToUpdate = productRepository.findById(orderItem.getProduct().getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Product with ID " + orderItem.getProduct().getProductId() +
                                        " associated with order item " + itemId + " not found during cancellation."));

                if (productToUpdate.getStatus() == ProductStatus.SOLD) {
                    productToUpdate.setStatus(ProductStatus.ACTIVE);
                    productToUpdate.setSoldAt(null);
                    productRepository.save(productToUpdate);
                    log.info("Product {} status set to ACTIVE due to order item {} cancellation.", productToUpdate.getProductId(), itemId);
                } else {
                    log.warn("Product {} was not in SOLD status (was {}), not changing status to ACTIVE for order item {} cancellation.",
                            productToUpdate.getProductId(), productToUpdate.getStatus(), itemId);
                }

                // transaction will not be null here
                transaction.setStatus(TransactionStatus.CANCELLED);
                transactionRepository.save(transaction);
                log.info("Order item {} cancelled, escrow refunded", itemId);
                break;
        }

        updateOverallOrderStatus(order);
        order = orderRepository.save(order); // Persist changes to order and its items

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

        Order order = getOrderWithValidation(orderId, userId, false); // Ensures user has relation to order
        OrderItem orderItem = order.getOrderItems().stream()
                .filter(item -> item.getOrderItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found: " + itemId));

        boolean isBuyer = order.getBuyer().getUserId().equals(userId);
        boolean isSeller = orderItem.getSeller().getUserId().equals(userId);

        if (!isItemCancellable(orderItem.getStatus())) {
            throw new BusinessLogicException(
                    "Cannot cancel item in '" + orderItem.getStatus() + "' status. " +
                            "Only PENDING and PROCESSING items can be cancelled by users through this flow."
            );
        }

        // Authorization check for who can cancel
        if (!isBuyer && !isSeller) { // Should be covered by getOrderWithValidation and item specific checks
            throw new UnauthorizedException("User not authorized to cancel this specific item.");
        }
        // Additional specific business rule: e.g., only buyer can cancel PENDING, seller can cancel PROCESSING etc.
        // This is partially handled in validateItemStatusUpdate called by updateOrderItemStatus.

        OrderStatusUpdateRequest cancelRequest = OrderStatusUpdateRequest.builder()
                .status(OrderItemStatus.CANCELLED.name())
                .notes(reason)
                .build();

        // This will call the main updateOrderItemStatus method which now contains all the logic
        return updateOrderItemStatus(orderId, itemId, cancelRequest, userId);
    }


    // This internal method is used by cancelOrder. It needs similar protections.
    private void cancelOrderItemInternal(OrderItem orderItem, String reason, User requestingUser) {
        log.info("Internal cancellation for order item: {}, reason: {}, requested by: {}",
                orderItem.getOrderItemId(), reason, requestingUser.getUsername());

        // Check if item can be cancelled based on status (already done by caller `cancelOrder`)
        // if (!isItemCancellable(orderItem.getStatus())) {
        //     log.warn("Item {} cannot be cancelled internally, status is {}", orderItem.getOrderItemId(), orderItem.getStatus());
        //     return; // Or throw, depending on how cancelOrder wants to handle this
        // }

        orderItem.setStatus(OrderItemStatus.CANCELLED);
        orderItem.setEscrowStatus(EscrowStatus.REFUNDED);

        // Edge Case 3: Product State Inconsistency
        Product productToUpdate = productRepository.findById(orderItem.getProduct().getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product with ID " + orderItem.getProduct().getProductId() +
                                " associated with order item " + orderItem.getOrderItemId() + " not found during internal cancellation."));

        if (productToUpdate.getStatus() == ProductStatus.SOLD) {
            productToUpdate.setStatus(ProductStatus.ACTIVE);
            productToUpdate.setSoldAt(null);
            productRepository.save(productToUpdate);
            log.info("Product {} status set to ACTIVE due to internal cancellation of order item {}.",
                    productToUpdate.getProductId(), orderItem.getOrderItemId());
        } else {
            log.warn("Product {} was not in SOLD status (was {}), not changing status to ACTIVE for internal cancellation of order item {}.",
                    productToUpdate.getProductId(), productToUpdate.getStatus(), orderItem.getOrderItemId());
        }

        // Edge Case 2: Transaction Record Discrepancy
        Transaction transaction = transactionRepository.findByOrderItemOrderItemId(orderItem.getOrderItemId())
                .orElseThrow(() -> new IllegalStateException(
                        "Transaction record not found for order item ID: " + orderItem.getOrderItemId() +
                                ", which is required for internal cancellation."));
        transaction.setStatus(TransactionStatus.CANCELLED);
        transactionRepository.save(transaction);
        log.info("Transaction {} for order item {} marked as CANCELLED.", transaction.getTransactionId(), orderItem.getOrderItemId());
    }


    @Transactional
    public OrderDetailResponse cancelOrder(Long orderId, String reason, Long userId) {
        log.info("Cancelling order: orderId={}, userId={}, reason={}", orderId, userId, reason);

        Order order = getOrderWithValidation(orderId, userId, false);
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));


        boolean hasShippedOrDeliveredItems = order.getOrderItems().stream()
                .anyMatch(item -> item.getStatus() == OrderItemStatus.SHIPPED ||
                        item.getStatus() == OrderItemStatus.DELIVERED);

        if (hasShippedOrDeliveredItems) {
            throw new BusinessLogicException(
                    "Cannot cancel order with shipped or delivered items. " +
                            "Please manage individual items or contact support."
            );
        }

        List<OrderItem> cancelledItems = new ArrayList<>();
        List<OrderItem> unCancellableItems = new ArrayList<>();

        for (OrderItem item : order.getOrderItems()) {
            if (isItemCancellable(item.getStatus())) {
                // Directly call the internal logic for cancellation side effects here
                // instead of updateOrderItemStatus to avoid N order saves in a loop
                cancelOrderItemInternal(item, reason, user);
                cancelledItems.add(item);
            } else {
                unCancellableItems.add(item);
            }
        }

        if (cancelledItems.isEmpty() && !unCancellableItems.isEmpty()) {
            // This means all items were in a non-cancellable state (e.g. already cancelled, shipped etc.)
            // but the initial check for shipped/delivered items passed.
            // For example, if all items were already cancelled individually.
            throw new BusinessLogicException("No items in this order are eligible for cancellation at this time.");
        }
        if (cancelledItems.isEmpty()){
            // Order has no items or items were already processed in a way they disappeared from these lists
            log.warn("Order {} has no items to cancel or all items were in an unexpected state.", orderId);
            // We might still want to update the overall order status if it's pending.
        }


        updateOverallOrderStatus(order); // Recalculate overall order status
        order = orderRepository.save(order); // Save all changes

        log.info("Order {} processed for cancellation: {} items cancelled, {} items were not cancellable in this operation",
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

        switch (newStatus) {
            case PAID:
                if (!isBuyer || !OrderStatus.PENDING.equals(order.getStatus())) {
                    throw new BusinessLogicException("Only pending orders can be marked as paid by the buyer.");
                }
                order.setStatus(OrderStatus.PAID);
                order.setPaidAt(LocalDateTime.now());
                order.getOrderItems().forEach(item -> {
                    if (item.getStatus() == OrderItemStatus.PENDING) {
                        item.setStatus(OrderItemStatus.PROCESSING);
                    }
                });
                break;

            case CANCELLED:
                User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
                if (!isBuyer && !isSellerInOrder(order, userId)) { // isSellerInOrder checks if current user is a seller of any item
                    throw new UnauthorizedException("Not authorized to cancel this order.");
                }
                // This manual order cancellation should also only affect cancellable items
                order.getOrderItems().forEach(item -> {
                    if (isItemCancellable(item.getStatus())) {
                        cancelOrderItemInternal(item, request.getNotes() != null ? request.getNotes() : "Order cancelled by user.", user);
                    }
                });
                break;

            default:
                throw new BusinessLogicException("Order status '" + newStatus +
                        "' is typically updated automatically based on item statuses. Use item-specific endpoints or allowed direct order status changes.");
        }

        updateOverallOrderStatus(order);
        order = orderRepository.save(order);

        return convertToDetailResponse(order);
    }

    public PagedResponse<OrderListResponse> getUserOrders(Long userId, String status, String role, Pageable pageable) {
        log.debug("Getting user orders: userId={}, status={}, role={}", userId, status, role);
        Page<Order> orders;

        if ("seller".equalsIgnoreCase(role)) {
            Page<OrderItem> sellerItems;
            if (status != null && !status.trim().isEmpty()) {
                try {
                    OrderItemStatus itemStatus = OrderItemStatus.valueOf(status.toUpperCase());
                    sellerItems = orderItemRepository.findBySellerUserIdAndStatusOrderByOrderCreatedAtDesc(userId, itemStatus, pageable);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid OrderItemStatus provided for seller: {}. Defaulting to all items.", status);
                    sellerItems = orderItemRepository.findBySellerUserIdOrderByOrderCreatedAtDesc(userId, pageable);
                }
            } else {
                sellerItems = orderItemRepository.findBySellerUserIdOrderByOrderCreatedAtDesc(userId, pageable);
            }
            List<Order> uniqueOrders = sellerItems.getContent().stream()
                    .map(OrderItem::getOrder)
                    .distinct()
                    .collect(Collectors.toList());
            orders = new PageImpl<>(uniqueOrders, pageable, sellerItems.getTotalElements());
        } else { // Default to buyer role
            if (status != null && !status.trim().isEmpty()) {
                try {
                    OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
                    orders = orderRepository.findByBuyerUserIdAndStatusOrderByCreatedAtDesc(userId, orderStatus, pageable);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid OrderStatus provided for buyer: {}. Defaulting to all orders.", status);
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

        Map<Long, List<OrderDetailResponse.OrderItemDetail>> sellerItemsMap = new HashMap<>();
        List<OrderSellersResponse.SellerSummary> sellerSummaries = new ArrayList<>();

        Map<User, List<OrderItem>> itemsBySellerPojo = order.getOrderItems().stream()
                .collect(Collectors.groupingBy(OrderItem::getSeller));

        for (Map.Entry<User, List<OrderItem>> entry : itemsBySellerPojo.entrySet()) {
            User seller = entry.getKey();
            List<OrderItem> items = entry.getValue();

            List<OrderDetailResponse.OrderItemDetail> itemDetails = items.stream()
                    .map(this::convertToItemDetail)
                    .collect(Collectors.toList());
            sellerItemsMap.put(seller.getUserId(), itemDetails);

            Set<OrderItemStatus> statuses = items.stream().map(OrderItem::getStatus).collect(Collectors.toSet());
            String overallStatus = statuses.size() == 1 ? statuses.iterator().next().name() : "MIXED";

            OrderSellersResponse.SellerSummary summary = OrderSellersResponse.SellerSummary.builder()
                    .sellerId(seller.getUserId())
                    .sellerUsername(seller.getUsername())
                    .sellerName(Optional.ofNullable(seller.getFirstName()).orElse("") + " " + Optional.ofNullable(seller.getLastName()).orElse(""))
                    .isLegitProfile(seller.getIsLegitProfile())
                    .itemCount(items.size())
                    .overallStatus(overallStatus)
                    .build();
            sellerSummaries.add(summary);
        }

        return OrderSellersResponse.builder()
                .sellerCount(sellerItemsMap.size())
                .itemsBySeller(sellerItemsMap)
                .sellerSummaries(sellerSummaries)
                .build();
    }

    private OrderItemData validateAndProcessItem(OrderCreateRequest.OrderItemRequest itemRequest, Long buyerId) {
        Product product = productRepository.findById(itemRequest.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.getProductId()));

        if (!ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new BusinessLogicException("Product '" + product.getTitle() + "' is not available for purchase (status: " + product.getStatus() + ").");
        }

        if (product.getSeller().getUserId().equals(buyerId)) {
            throw new BusinessLogicException("Cannot purchase your own product: " + product.getTitle());
        }

        BigDecimal itemPrice;
        Offer offer = null;

        if (itemRequest.getOfferId() != null) {
            offer = offerRepository.findById(itemRequest.getOfferId())
                    .orElseThrow(() -> new ResourceNotFoundException("Offer not found: " + itemRequest.getOfferId()));
            if (!offer.getProduct().getProductId().equals(product.getProductId())) {
                throw new BusinessLogicException("Offer " + offer.getOfferId() + " does not match product " + product.getProductId());
            }
            if (!OfferStatus.ACCEPTED.equals(offer.getStatus())) {
                throw new BusinessLogicException("Offer is not accepted for product: " + product.getTitle() + " (offer status: " + offer.getStatus() + ").");
            }
            if (!offer.getBuyer().getUserId().equals(buyerId)) {
                throw new UnauthorizedException("Offer " + offer.getOfferId() + " does not belong to buyer " + buyerId);
            }
            itemPrice = offer.getAmount();
        } else {
            itemPrice = product.getPrice();
        }

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

        if (itemStatuses.isEmpty() && order.getStatus() == OrderStatus.PENDING) {
            log.warn("Order {} has no items but is PENDING. Setting to CANCELLED.", order.getOrderId());
            order.setStatus(OrderStatus.CANCELLED); // Or handle as an error, an order shouldn't really exist without items
            return;
        }
        if (itemStatuses.isEmpty()) { // If items were removed entirely somehow
            log.warn("Order {} has no items. Current status: {}. No status change applied.", order.getOrderId(), order.getStatus());
            return;
        }


        long totalItems = itemStatuses.size();
        long deliveredItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.DELIVERED).count();
        long cancelledItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.CANCELLED).count();
        long refundedItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.REFUNDED).count(); // Assuming REFUNDED is a terminal state like CANCELLED
        long processingItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.PROCESSING).count();
        long pendingItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.PENDING).count();
        long shippedItems = itemStatuses.stream().filter(s -> s == OrderItemStatus.SHIPPED).count();


        if (pendingItems == totalItems && order.getStatus() != OrderStatus.PENDING) { // If all items somehow reverted to pending
            order.setStatus(OrderStatus.PENDING);
        } else if (processingItems > 0 && pendingItems == 0 && shippedItems == 0 && deliveredItems == 0 && order.getStatus() != OrderStatus.PAID) {
            // All items are either processing, cancelled, or refunded, but at least one is processing
            // and none are further along the happy path.
            order.setStatus(OrderStatus.PAID); // Implies payment was made, items are being processed
        } else if (shippedItems > 0 && deliveredItems == 0 && order.getStatus() != OrderStatus.SHIPPED) {
            // At least one item is shipped, none are delivered yet. Other items could be processing/cancelled/refunded.
            order.setStatus(OrderStatus.SHIPPED);
        } else if (deliveredItems > 0 && deliveredItems + cancelledItems + refundedItems == totalItems) {
            // All items are either delivered, cancelled, or refunded, and at least one is delivered.
            order.setStatus(OrderStatus.DELIVERED);
        } else if (cancelledItems + refundedItems == totalItems) {
            // All items are cancelled or refunded.
            order.setStatus(OrderStatus.CANCELLED); // Or REFUNDED if that's a distinct overall state
        } else if (pendingItems > 0) { // If any item is still pending, order is pending (unless already handled)
            if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PAID) { // Avoid regressing from PAID if some items are PENDING and others PROCESSING
                order.setStatus(OrderStatus.PENDING);
            }
        }
        // If none of the above, the status might be a mix (e.g. some processing, some shipped - this is SHIPPED by above rule)
        // Or some shipped, some delivered - this would be DELIVERED if all non-cancelled/refunded are delivered
        log.debug("Overall order status for order {} determined as: {}", order.getOrderId(), order.getStatus());
    }


    private void validateItemStatusUpdate(OrderItemStatus currentStatus, OrderItemStatus newStatus, boolean isBuyer, boolean isSeller) {
        // More granular checks can be added here based on who is initiating the change.
        // For example, a seller cannot mark an item as DELIVERED. A buyer cannot mark as SHIPPED.
        switch (newStatus) {
            case PROCESSING: // Typically after payment confirmation, usually system-driven or by admin. Buyer can't directly set this.
                if (!OrderItemStatus.PENDING.equals(currentStatus)) {
                    throw new BusinessLogicException("Item can only move to PROCESSING from PENDING status.");
                }
                // This is usually an internal status change post-payment, not directly by user role in this method.
                break;
            case SHIPPED:
                if (!isSeller) {
                    throw new UnauthorizedException("Only the seller can mark an item as SHIPPED.");
                }
                if (!OrderItemStatus.PROCESSING.equals(currentStatus)) {
                    throw new BusinessLogicException("Item can only be SHIPPED from PROCESSING status.");
                }
                break;
            case DELIVERED:
                if (!isBuyer) {
                    throw new UnauthorizedException("Only the buyer can confirm an item as DELIVERED.");
                }
                if (!OrderItemStatus.SHIPPED.equals(currentStatus)) {
                    throw new BusinessLogicException("Item can only be DELIVERED from SHIPPED status.");
                }
                break;
            case CANCELLED:
                if (!isItemCancellable(currentStatus)) { // isItemCancellable checks for PENDING or PROCESSING
                    throw new BusinessLogicException("Item in status " + currentStatus + " cannot be cancelled through this flow.");
                }
                if (!isBuyer && !isSeller) { // Ensure either buyer or seller is making the request
                    throw new UnauthorizedException("User is not authorized to cancel this item.");
                }
                // Further rules like "seller can only cancel if PROCESSING, buyer if PENDING" could be added if needed.
                break;
            case PENDING:
            case REFUNDED: // These are typically results of other processes, not direct updates via this generic method.
                throw new BusinessLogicException("Status " + newStatus + " cannot be set directly via this operation.");
            default:
                // Should be caught by Enum.valueOf earlier, but as a safeguard:
                throw new BusinessLogicException("Unsupported or invalid status transition to: " + newStatus);
        }
    }

    private boolean isItemCancellable(OrderItemStatus status) {
        return status == OrderItemStatus.PENDING || status == OrderItemStatus.PROCESSING;
    }

    private boolean isSellerInOrder(Order order, Long userId) {
        return order.getOrderItems().stream()
                .anyMatch(item -> item.getSeller().getUserId().equals(userId));
    }

    private Order getOrderWithValidation(Long orderId, Long userId, boolean buyerOnly) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        boolean isBuyer = order.getBuyer().getUserId().equals(userId);
        boolean isSellerOfAtLeastOneItem = order.getOrderItems().stream()
                .anyMatch(item -> item.getSeller().getUserId().equals(userId));

        if (buyerOnly) {
            if (!isBuyer) {
                throw new UnauthorizedException("User " + userId + " is not the buyer of order " + orderId);
            }
        } else { // Accessible by buyer or any seller involved in the order
            if (!isBuyer && !isSellerOfAtLeastOneItem) {
                throw new UnauthorizedException("User " + userId + " is not authorized to access order " + orderId);
            }
        }
        return order;
    }

    // --- Conversion and Helper Methods ---
    private OrderListResponse convertToListResponse(Order order) {
        List<OrderListResponse.OrderItemSummary> itemSummaries = order.getOrderItems().stream()
                .limit(3)
                .map(this::convertToItemSummary)
                .collect(Collectors.toList());

        Set<Long> uniqueSellerIds = order.getOrderItems().stream()
                .map(item -> item.getSeller().getUserId())
                .collect(Collectors.toSet());

        String overallEscrowStatus = "MIXED";
        if (!order.getOrderItems().isEmpty()) {
            Set<EscrowStatus> escrowStatuses = order.getOrderItems().stream()
                    .map(OrderItem::getEscrowStatus)
                    .collect(Collectors.toSet());
            if (escrowStatuses.size() == 1) {
                overallEscrowStatus = escrowStatuses.iterator().next().name();
            }
        } else {
            overallEscrowStatus = "N/A"; // No items
        }


        return OrderListResponse.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                // .totalShippingFee(order.getTotalShippingFee()) // If you add this to Order POJO
                .totalItems(order.getOrderItems().size())
                .uniqueSellers(uniqueSellerIds.size())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .deliveredAt(order.getDeliveredAt()) // If you add this to Order POJO
                .itemSummaries(itemSummaries)
                .overallEscrowStatus(overallEscrowStatus)
                .totalPlatformFee(order.getOrderItems().stream()
                        .map(OrderItem::getPlatformFee)
                        .filter(Objects::nonNull)
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
                    .orElseGet(() -> item.getProduct().getImages().getFirst().getImageUrl()); // Fallback to first image
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

        Map<String, List<OrderDetailResponse.OrderItemDetail>> itemsBySellerUsername = orderItemDetails.stream()
                .collect(Collectors.groupingBy(itemDetail -> itemDetail.getSeller().getUsername()));

        BigDecimal totalPlatformFee = order.getOrderItems().stream()
                .map(OrderItem::getPlatformFee)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<Long> uniqueSellerIds = order.getOrderItems().stream()
                .map(item -> item.getSeller().getUserId())
                .collect(Collectors.toSet());

        OrderDetailResponse.OrderEscrowSummary escrowSummary = buildEscrowSummary(order.getOrderItems());

        UserAddress sa = order.getShippingAddress();
        OrderDetailResponse.ShippingAddressInfo shippingAddressInfo = (sa != null) ?
                OrderDetailResponse.ShippingAddressInfo.builder()
                        .addressId(sa.getAddressId())
                        .addressLine1(sa.getAddressLine1())
                        .addressLine2(sa.getAddressLine2())
                        .city(sa.getCity())
                        .state(sa.getState())
                        .postalCode(sa.getPostalCode())
                        .country(sa.getCountry())
                        .build() : null;

        return OrderDetailResponse.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                // .totalShippingFee(order.getTotalShippingFee()) // If added
                .totalPlatformFee(totalPlatformFee)
                // .notes(order.getNotes()) // If added to Order POJO
                .totalItems(order.getOrderItems().size())
                .uniqueSellers(uniqueSellerIds.size())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .shippedAt(order.getShippedAt()) // If added to Order POJO
                .deliveredAt(order.getDeliveredAt()) // If added to Order POJO
                .orderItems(orderItemDetails)
                .shippingAddress(shippingAddressInfo)
                .escrowSummary(escrowSummary)
                .itemsBySeller(itemsBySellerUsername)
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
                    .orElseGet(() -> product.getImages().getFirst().getImageUrl());
        }

        OrderDetailResponse.ProductInfo productInfo = OrderDetailResponse.ProductInfo.builder()
                .productId(product.getProductId())
                .title(product.getTitle())
                .description(product.getDescription())
                .condition(product.getCondition().name())
                .size(product.getSize())
                .color(product.getColor())
                .primaryImageUrl(primaryImageUrl)
                // .shippingFee(product.getShippingFee()) // If Product has shipping fee
                .categoryId(product.getCategory().getCategoryId())
                .categoryName(product.getCategory().getName())
                .brandId(product.getBrand().getBrandId())
                .brandName(product.getBrand().getName())
                .build();

        User seller = item.getSeller();
        OrderDetailResponse.SellerInfo sellerInfo = OrderDetailResponse.SellerInfo.builder()
                .userId(seller.getUserId())
                .username(seller.getUsername())
                .firstName(seller.getFirstName())
                .lastName(seller.getLastName())
                .isLegitProfile(seller.getIsLegitProfile())
                .sellerRating(seller.getSellerRating())
                .sellerReviewsCount(seller.getSellerReviewsCount())
                .build();

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
                // .notes(item.getNotes()) // If OrderItem has notes
                .product(productInfo)
                .seller(sellerInfo)
                .transaction(transactionInfo)
                .build();
    }

    private OrderDetailResponse.OrderEscrowSummary buildEscrowSummary(List<OrderItem> orderItems) {
        if (orderItems.isEmpty()) {
            return OrderDetailResponse.OrderEscrowSummary.builder()
                    .totalEscrowAmount(BigDecimal.ZERO)
                    .totalPlatformFee(BigDecimal.ZERO)
                    .totalSellerAmount(BigDecimal.ZERO)
                    .itemsInEscrow(0).itemsReleased(0).itemsRefunded(0)
                    .sellerEscrows(Collections.emptyList())
                    .build();
        }

        BigDecimal totalEscrowAmount = orderItems.stream().map(OrderItem::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPlatformFee = orderItems.stream().map(OrderItem::getPlatformFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSellerAmount = totalEscrowAmount.subtract(totalPlatformFee);

        Map<EscrowStatus, Long> statusCounts = orderItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getEscrowStatus, Collectors.counting()));

        List<OrderDetailResponse.SellerEscrowInfo> sellerEscrows = orderItems.stream()
                .collect(Collectors.groupingBy(item -> item.getSeller().getUserId()))
                .entrySet().stream()
                .map(entry -> {
                    User seller = userRepository.findById(entry.getKey()).orElseThrow(); // Should exist
                    List<OrderItem> sellerOrderItems = entry.getValue();
                    BigDecimal sellerItemsTotal = sellerOrderItems.stream().map(OrderItem::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal sellerItemsFee = sellerOrderItems.stream().map(OrderItem::getPlatformFee).reduce(BigDecimal.ZERO, BigDecimal::add);
                    Set<EscrowStatus> sellerEscrowStatuses = sellerOrderItems.stream().map(OrderItem::getEscrowStatus).collect(Collectors.toSet());
                    String escrowStatusName = sellerEscrowStatuses.size() == 1 ? sellerEscrowStatuses.iterator().next().name() : "MIXED";

                    return OrderDetailResponse.SellerEscrowInfo.builder()
                            .sellerId(seller.getUserId())
                            .sellerUsername(seller.getUsername())
                            .escrowAmount(sellerItemsTotal)
                            .platformFee(sellerItemsFee)
                            .sellerAmount(sellerItemsTotal.subtract(sellerItemsFee))
                            .escrowStatus(escrowStatusName)
                            .itemCount(sellerOrderItems.size())
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