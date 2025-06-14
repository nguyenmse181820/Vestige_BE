package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.OrderCreateRequest;
import se.vestige_be.dto.request.OrderStatusUpdateRequest;
import se.vestige_be.dto.response.*;
import se.vestige_be.pojo.User;
import se.vestige_be.service.OrderService;
import se.vestige_be.service.UserService;
import se.vestige_be.util.PaginationUtils;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Order", description = "API for order management")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> createOrder(
            @Valid @RequestBody OrderCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            OrderDetailResponse order = orderService.createOrder(request, user.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.<OrderDetailResponse>builder()
                            .message("Order created successfully")
                            .data(order)
                            .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<OrderDetailResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PagedResponse<OrderListResponse>>> getUserOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "buyer") String role,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);

        PagedResponse<OrderListResponse> orders = orderService.getUserOrders(
                user.getUserId(), status, role, pageable);

        return ResponseEntity.ok(ApiResponse.<PagedResponse<OrderListResponse>>builder()
                .message("Orders retrieved successfully")
                .data(orders)
                .build());
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrderById(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            OrderDetailResponse order = orderService.getOrderById(orderId, user.getUserId());
            return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                    .message("Order retrieved successfully")
                    .data(order)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<OrderDetailResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/{orderId}/confirm-payment")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> confirmPayment(
            @PathVariable Long orderId,
            @RequestBody(required = false) String stripePaymentIntentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            OrderDetailResponse order = orderService.confirmPayment(orderId, user.getUserId(), stripePaymentIntentId);
            return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                    .message("Payment confirmed successfully")
                    .data(order)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<OrderDetailResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @PatchMapping("/{orderId}/items/{itemId}/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> updateOrderItemStatus(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody OrderStatusUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            OrderDetailResponse order = orderService.updateOrderItemStatus(orderId, itemId, request, user.getUserId());
            return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                    .message("Order item status updated successfully")
                    .data(order)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<OrderDetailResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/{orderId}/items/{itemId}/ship")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> shipOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid @RequestBody OrderStatusUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            // Set status to SHIPPED in the request
            request.setStatus("SHIPPED");
            OrderDetailResponse order = orderService.updateOrderItemStatus(orderId, itemId, request, user.getUserId());
            return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                    .message("Item shipped successfully")
                    .data(order)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<OrderDetailResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/{orderId}/items/{itemId}/confirm-delivery")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> confirmItemDelivery(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            // Create request with DELIVERED status
            OrderStatusUpdateRequest request = OrderStatusUpdateRequest.builder()
                    .status("DELIVERED")
                    .notes(notes)
                    .build();

            OrderDetailResponse order = orderService.updateOrderItemStatus(orderId, itemId, request, user.getUserId());
            return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                    .message("Item delivery confirmed successfully")
                    .data(order)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<OrderDetailResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> cancelOrder(
            @PathVariable Long orderId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            OrderDetailResponse order = orderService.cancelOrder(orderId, reason, user.getUserId());
            return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                    .message("Order cancelled successfully")
                    .data(order)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<OrderDetailResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/{orderId}/items/{itemId}/cancel")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> cancelOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            OrderStatusUpdateRequest request = OrderStatusUpdateRequest.builder()
                    .status("CANCELLED")
                    .notes(reason)
                    .build();

            OrderDetailResponse order = orderService.updateOrderItemStatus(orderId, itemId, request, user.getUserId());
            return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                    .message("Order item cancelled successfully")
                    .data(order)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<OrderDetailResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/{orderId}/tracking")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Object>> getOrderTracking(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            OrderDetailResponse order = orderService.getOrderById(orderId, user.getUserId());

            // Extract tracking information from order items
            Object trackingInfo = order.getOrderItems().stream()
                    .filter(item -> item.getTransaction() != null &&
                            item.getTransaction().getTrackingNumber() != null)
                    .map(item -> {
                        var transaction = item.getTransaction();
                        return java.util.Map.of(
                                "itemId", item.getOrderItemId(),
                                "productTitle", item.getProduct().getTitle(),
                                "trackingNumber", transaction.getTrackingNumber(),
                                "trackingUrl", transaction.getTrackingUrl() != null ?
                                        transaction.getTrackingUrl() : "",
                                "status", item.getStatus(),
                                "shippedAt", transaction.getShippedAt(),
                                "deliveredAt", transaction.getDeliveredAt()
                        );
                    })
                    .toList();

            return ResponseEntity.ok(ApiResponse.builder()
                    .message("Order tracking retrieved successfully")
                    .data(trackingInfo)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Object>> getUserOrderStats(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            Object stats = java.util.Map.of(
                    "totalOrders", 0,
                    "activeOrders", 0,
                    "completedOrders", 0,
                    "cancelledOrders", 0,
                    "totalSpent", 0,
                    "totalEarned", 0
            );

            return ResponseEntity.ok(ApiResponse.builder()
                    .message("Order statistics retrieved successfully")
                    .data(stats)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.builder()
                            .message(e.getMessage())
                            .build());
        }
    }
}