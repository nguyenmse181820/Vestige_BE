package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.request.ConfirmPickupRequest;
import se.vestige_be.dto.response.OrderDetailResponse;
import se.vestige_be.dto.response.PickupItemResponse;
import se.vestige_be.dto.response.UserAddressResponse;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.exception.ResourceNotFoundException;
import se.vestige_be.pojo.Order;
import se.vestige_be.pojo.OrderItem;
import se.vestige_be.pojo.PickupEvidence;
import se.vestige_be.pojo.DeliveryEvidence;
import se.vestige_be.pojo.Transaction;
import se.vestige_be.pojo.enums.EscrowStatus;
import se.vestige_be.pojo.enums.OrderItemStatus;
import se.vestige_be.pojo.enums.TransactionStatus;
import se.vestige_be.repository.OrderItemRepository;
import se.vestige_be.repository.OrderRepository;
import se.vestige_be.repository.PickupEvidenceRepository;
import se.vestige_be.repository.DeliveryEvidenceRepository;
import se.vestige_be.repository.TransactionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class LogisticsService {

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final PickupEvidenceRepository pickupEvidenceRepository;
    private final DeliveryEvidenceRepository deliveryEvidenceRepository;
    private final EscrowService escrowService;
    private final OrderService orderService;
    private final StatusHistoryService statusHistoryService;
    private final UserAddressService userAddressService;

    public List<PickupItemResponse> getItemsAwaitingPickup() {
        log.info("Retrieving all items awaiting pickup");
        List<OrderItem> items = orderItemRepository.findByStatusWithDetails(OrderItemStatus.AWAITING_PICKUP);
        return items.stream()
                .map(this::convertToPickupItemResponse)
                .toList();
    }

    @Transactional
    public OrderDetailResponse confirmPickup(ConfirmPickupRequest request) {
        log.info("Confirming pickup for order item: {} with {} evidence photos", 
                request.getOrderItemId(), request.getPhotoUrls().size());
        
        // Find the transaction by order item ID
        Transaction transaction = transactionRepository.findByOrderItemOrderItemId(request.getOrderItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found for order item: " + request.getOrderItemId()));

        OrderItem orderItem = transaction.getOrderItem();
        
        // Validate current status
        if (orderItem.getStatus() != OrderItemStatus.AWAITING_PICKUP) {
            throw new BusinessLogicException("Item must be in AWAITING_PICKUP status to confirm pickup. Current status: " + orderItem.getStatus());
        }

        // Change status to IN_WAREHOUSE
        orderItem.setStatus(OrderItemStatus.IN_WAREHOUSE);

        // Record status change in history
        statusHistoryService.recordStatusChange(
            orderItem, 
            OrderItemStatus.IN_WAREHOUSE, 
            getCurrentUsername(), 
            "Item picked up from seller. " + request.getPhotoUrls().size() + " evidence photos recorded."
        );

        // Generate internal tracking number
        String trackingNumber = "VSTG-" + orderItem.getOrderItemId();
        transaction.setTrackingNumber(trackingNumber);
        
        // Save evidence photos
        for (String imageUrl : request.getPhotoUrls()) {
            PickupEvidence evidence = PickupEvidence.builder()
                    .transaction(transaction)
                    .imageUrl(imageUrl)
                    .uploadedAt(LocalDateTime.now())
                    .build();
            pickupEvidenceRepository.save(evidence);
        }
        
        // Save transaction and order item
        transactionRepository.save(transaction);
        orderItemRepository.save(orderItem);

        // Update overall order status and return response
        Order order = orderItem.getOrder();
        updateOverallOrderStatus(order);
        orderRepository.save(order);

        log.info("Pickup confirmed for order item {}. Tracking number: {}. Evidence photos saved: {}", 
                request.getOrderItemId(), trackingNumber, request.getPhotoUrls().size());
        return convertToDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse dispatchItem(Long itemId) {
        log.info("Dispatching item for delivery: {}", itemId);
        
        OrderItem orderItem = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found: " + itemId));

        // Validate current status
        if (orderItem.getStatus() != OrderItemStatus.IN_WAREHOUSE) {
            throw new BusinessLogicException("Item must be in IN_WAREHOUSE status to dispatch. Current status: " + orderItem.getStatus());
        }

        // Change status to OUT_FOR_DELIVERY
        orderItem.setStatus(OrderItemStatus.OUT_FOR_DELIVERY);

        // Record status change in history
        Transaction transaction = getTransactionForOrderItem(orderItem.getOrderItemId());
        String trackingInfo = transaction.getTrackingNumber() != null ? 
            "Tracking: " + transaction.getTrackingNumber() : "No tracking number assigned";
        statusHistoryService.recordStatusChange(
            orderItem, 
            OrderItemStatus.OUT_FOR_DELIVERY, 
            getCurrentUsername(), 
            "Item dispatched for delivery. " + trackingInfo
        );
        orderItemRepository.save(orderItem);

        // Update overall order status and return response
        Order order = orderItem.getOrder();
        updateOverallOrderStatus(order);
        orderRepository.save(order);

        log.info("Item {} dispatched for delivery", itemId);
        return convertToDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse confirmDelivery(Long itemId, List<String> photoUrls) {
        log.info("Confirming delivery for item: {} with {} photo URLs", itemId, photoUrls.size());

        if (photoUrls == null || photoUrls.isEmpty()) {
            throw new BusinessLogicException("At least one photo URL is required as proof of delivery.");
        }

        OrderItem orderItem = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found: " + itemId));

        if (orderItem.getStatus() != OrderItemStatus.OUT_FOR_DELIVERY) {
            throw new BusinessLogicException("Item must be in OUT_FOR_DELIVERY status to confirm delivery. Current status: " + orderItem.getStatus());
        }

        orderItem.setStatus(OrderItemStatus.DELIVERED);

        Transaction transaction = getTransactionForOrderItem(orderItem.getOrderItemId());
        transaction.setStatus(TransactionStatus.DELIVERED);
        transaction.setEscrowStatus(EscrowStatus.RELEASED);
        transaction.setDeliveredAt(LocalDateTime.now());

        // 1. Get the existing list from the managed entity
        List<DeliveryEvidence> evidenceList = transaction.getDeliveryEvidence();
        if (evidenceList == null) { // Defensive check, though @Builder.Default should prevent this
            evidenceList = new ArrayList<>();
            transaction.setDeliveryEvidence(evidenceList);
        }

        // 2. Clear the existing list (Hibernate will track removals)
        evidenceList.clear();

        // 3. Create and add the new evidence to the SAME list instance
        for (String url : photoUrls) {
            DeliveryEvidence evidence = DeliveryEvidence.builder()
                    .transaction(transaction)
                    .imageUrl(url)
                    .uploadedAt(LocalDateTime.now())
                    .build();
            evidenceList.add(evidence);
        }

        statusHistoryService.recordStatusChange(
                orderItem,
                OrderItemStatus.DELIVERED,
                getCurrentUsername(),
                "Item successfully delivered to buyer. " + photoUrls.size() + " evidence photos recorded."
        );

        // Save entities
        transactionRepository.save(transaction); // Saving the parent will cascade to the children
        orderItemRepository.save(orderItem);

        Order order = orderItem.getOrder();
        order.setDeliveredAt(LocalDateTime.now());
        updateOverallOrderStatus(order);
        orderRepository.save(order);

        escrowService.releaseEscrowFunds(orderItem, "Item delivered by Vestige Shipping with photo proof.");

        log.info("Delivery confirmed for item {}. Saved {} evidence photos. Escrow funds released.", itemId, photoUrls.size());
        return convertToDetailResponse(order);
    }

    // Helper methods

    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.warn("Failed to get current username from security context", e);
        }
        return "SYSTEM"; // fallback for system operations
    }

    private Transaction getTransactionForOrderItem(Long orderItemId) {
        return transactionRepository.findByOrderItemOrderItemId(orderItemId)
                .orElseThrow(() -> new IllegalStateException("Transaction not found for order item: " + orderItemId));
    }

    private void updateOverallOrderStatus(Order order) {
        // Delegate to OrderService for consistent status update logic
        orderService.updateOverallOrderStatus(order);
    }

    private OrderDetailResponse convertToDetailResponse(Order order) {
        // Delegate to OrderService for consistent response conversion
        return orderService.convertToDetailResponse(order);
    }

    private PickupItemResponse convertToPickupItemResponse(OrderItem orderItem) {
        // Get the corresponding transaction for this order item
        Transaction transaction = transactionRepository.findByOrderItemOrderItemId(orderItem.getOrderItemId())
                .orElse(null);
        
        // Get seller's default address for pickup location
        List<UserAddressResponse> sellerAddresses = userAddressService.getUserAddresses(orderItem.getSeller().getUserId());
        UserAddressResponse defaultAddress = sellerAddresses.stream()
                .filter(UserAddressResponse::getIsDefault)
                .findFirst()
                .orElse(sellerAddresses.isEmpty() ? null : sellerAddresses.get(0)); // Fallback to first address if no default

        return PickupItemResponse.builder()
                .orderItemId(orderItem.getOrderItemId())
                .orderId(orderItem.getOrder().getOrderId())
                .orderCode("ORD-" + orderItem.getOrder().getOrderId()) // Generate order code
                .productId(orderItem.getProduct().getProductId())
                .productName(orderItem.getProduct().getTitle()) // Use title field
                .productSlug(orderItem.getProduct().getSlug())
                .sellerId(orderItem.getSeller().getUserId())
                .sellerUsername(orderItem.getSeller().getUsername())
                .sellerFirstName(orderItem.getSeller().getFirstName())
                .sellerLastName(orderItem.getSeller().getLastName())
                
                // Add seller address information for pickup
                .sellerAddressId(defaultAddress != null ? defaultAddress.getAddressId() : null)
                .sellerAddressLine1(defaultAddress != null ? defaultAddress.getAddressLine1() : null)
                .sellerAddressLine2(defaultAddress != null ? defaultAddress.getAddressLine2() : null)
                .sellerCity(defaultAddress != null ? defaultAddress.getCity() : null)
                .sellerState(defaultAddress != null ? defaultAddress.getState() : null)
                .sellerPostalCode(defaultAddress != null ? defaultAddress.getPostalCode() : null)
                .sellerCountry(defaultAddress != null ? defaultAddress.getCountry() : null)
                
                .price(orderItem.getPrice())
                .platformFee(orderItem.getPlatformFee())
                .feePercentage(orderItem.getFeePercentage())
                .status(orderItem.getStatus())
                .escrowStatus(orderItem.getEscrowStatus())
                .createdAt(orderItem.getCreatedAt())
                .updatedAt(orderItem.getUpdatedAt())
                .build();
    }
}
