package se.vestige_be.service;

import com.stripe.model.Dispute;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
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
            } catch (Exception e) {
                log.error("Failed to create Stripe payment for order {}: {}", order.getOrderId(), e.getMessage());
                throw new BusinessLogicException("Could not initialize payment. Please try again later.");
            }
        }

        List<OrderItem> orderItems = createOrderItems(itemDataList, order);
        order.setOrderItems(orderItems);
        createTransactions(orderItems, buyer, shippingAddress, paymentIntentId);
        markProductsAsPendingPayment(itemDataList);
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

        try {
            boolean paymentSuccessful = stripeService.verifyPayment(stripePaymentIntentId);
            if (!paymentSuccessful) {
                throw new BusinessLogicException("Payment verification failed");
            }
        } catch (Exception e) {
            log.error("Payment verification failed for order {}: {}", orderId, e.getMessage());
            throw new BusinessLogicException("Payment verification failed");
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());

        order.getOrderItems().forEach(item -> {
            if (item.getStatus() == OrderItemStatus.PENDING) {
                item.setStatus(OrderItemStatus.PROCESSING);
                Product product = item.getProduct();
                product.setStatus(ProductStatus.SOLD);
                product.setSoldAt(LocalDateTime.now());
                productRepository.save(product);
            }
        });

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
            case SHIPPED:
                handleItemShipped(orderItem, request);
                break;
            case DELIVERED:
                handleItemDelivered(orderItem);
                break;
            case CANCELLED:
                handleItemCancelled(orderItem);
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
    }

    public PagedResponse<OrderListResponse> getUserOrders(Long userId, String status, String role, Pageable pageable) {
        Page<Order> orders;

        if ("seller".equalsIgnoreCase(role)) {
            orders = getSellerOrders(userId, status, pageable);
        } else {
            orders = getBuyerOrders(userId, status, pageable);
        }

        Page<OrderListResponse> orderResponses = orders.map(this::convertToListResponse);
        return PagedResponse.of(orderResponses);
    }

    public OrderDetailResponse getOrderById(Long orderId, Long userId) {
        Order order = getOrderWithValidation(orderId, userId, false);
        return convertToDetailResponse(order);
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
                .map(data -> OrderItem.builder()
                        .order(order)
                        .product(data.getProduct())
                        .seller(data.getProduct().getSeller())
                        .price(data.getItemPrice())
                        .platformFee(data.getPlatformFee())
                        .feePercentage(data.getFeePercentage())
                        .status(OrderItemStatus.PENDING)
                        .escrowStatus(EscrowStatus.HOLDING)
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
                    .escrowStatus(EscrowStatus.HOLDING)
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
        Page<OrderItem> sellerItems;
        if (status != null && !status.trim().isEmpty()) {
            try {
                OrderItemStatus itemStatus = OrderItemStatus.valueOf(status.toUpperCase());
                sellerItems = orderItemRepository.findBySellerUserIdAndStatusOrderByOrderCreatedAtDesc(userId, itemStatus, pageable);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid OrderItemStatus: {}. Returning all items", status);
                sellerItems = orderItemRepository.findBySellerUserIdOrderByOrderCreatedAtDesc(userId, pageable);
            }
        } else {
            sellerItems = orderItemRepository.findBySellerUserIdOrderByOrderCreatedAtDesc(userId, pageable);
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
            // (Optional) Gửi email/thông báo cho admin
        });
    }

    @Transactional
    public void handleTransferFailed(Transfer transfer) {
        // Lấy transactionId từ metadata mà chúng ta đã lưu khi tạo transfer
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
                transaction.setStripeTransferId(transfer.getId()); // Lưu lại mã transfer
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
                    .orElse(null);

            if (paymentIntentId != null) {
                try {
                    if (stripeService.verifyPayment(paymentIntentId)) {
                        log.warn("Reconciliation: Order {} was paid on Stripe but stuck in PENDING. Updating status now.", order.getOrderId());
                        handleSuccessfulPayment(paymentIntentId);
                    }
                } catch (Exception e) {
                    log.error("Error during reconciliation check for order {}: {}", order.getOrderId(), e.getMessage());
                }
            }
        }
        log.info("Reconciliation job finished.");
    }

    // Conversion methods
    private OrderListResponse convertToListResponse(Order order) {
        List<OrderListResponse.OrderItemSummary> itemSummaries = order.getOrderItems().stream()
                .limit(3)
                .map(this::convertToItemSummary)
                .collect(Collectors.toList());

        return OrderListResponse.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .totalItems(order.getOrderItems().size())
                .uniqueSellers((int) order.getOrderItems().stream()
                        .map(item -> item.getSeller().getUserId())
                        .distinct().count())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .deliveredAt(order.getDeliveredAt())
                .itemSummaries(itemSummaries)
                .build();
    }

    private OrderListResponse.OrderItemSummary convertToItemSummary(OrderItem item) {
        String primaryImageUrl = item.getProduct().getImages().stream()
                .filter(ProductImage::getIsPrimary)
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElseGet(() -> item.getProduct().getImages().isEmpty() ? null :
                        item.getProduct().getImages().get(0).getImageUrl());

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

        return OrderDetailResponse.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .totalItems(order.getOrderItems().size())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .orderItems(orderItemDetails)
                .metadata(new HashMap<>())
                .build();
    }

    private OrderDetailResponse.OrderItemDetail convertToItemDetail(OrderItem item) {
        String primaryImageUrl = item.getProduct().getImages().stream()
                .filter(ProductImage::getIsPrimary)
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElseGet(() -> item.getProduct().getImages().isEmpty() ? null :
                        item.getProduct().getImages().get(0).getImageUrl());

        return OrderDetailResponse.OrderItemDetail.builder()
                .orderItemId(item.getOrderItemId())
                .price(item.getPrice())
                .platformFee(item.getPlatformFee())
                .feePercentage(item.getFeePercentage())
                .status(item.getStatus().name())
                .escrowStatus(item.getEscrowStatus().name())
                .product(OrderDetailResponse.ProductInfo.builder()
                        .productId(item.getProduct().getProductId())
                        .title(item.getProduct().getTitle())
                        .description(item.getProduct().getDescription())
                        .condition(item.getProduct().getCondition().name())
                        .size(item.getProduct().getSize())
                        .color(item.getProduct().getColor())
                        .primaryImageUrl(primaryImageUrl)
                        .categoryId(item.getProduct().getCategory().getCategoryId())
                        .categoryName(item.getProduct().getCategory().getName())
                        .brandId(item.getProduct().getBrand().getBrandId())
                        .brandName(item.getProduct().getBrand().getName())
                        .build())
                .seller(OrderDetailResponse.SellerInfo.builder()
                        .userId(item.getSeller().getUserId())
                        .username(item.getSeller().getUsername())
                        .firstName(item.getSeller().getFirstName())
                        .lastName(item.getSeller().getLastName())
                        .isLegitProfile(item.getSeller().getIsLegitProfile())
                        .sellerRating(item.getSeller().getSellerRating())
                        .sellerReviewsCount(item.getSeller().getSellerReviewsCount())
                        .build())
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