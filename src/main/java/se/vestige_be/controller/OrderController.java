package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.OrderCreateRequest;
import se.vestige_be.dto.request.OrderStatusUpdateRequest;
import se.vestige_be.dto.request.PaymentConfirmationRequest;
import se.vestige_be.dto.request.OrderActionRequest;
import se.vestige_be.dto.response.*;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.pojo.User;
import se.vestige_be.service.OrderService;
import se.vestige_be.service.UserService;
import se.vestige_be.service.PayOsPaymentService;
import se.vestige_be.util.PaginationUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Order Management", 
     description = "Complete order management API with role-based access control. " +
                   "Supports order creation, payment processing, status tracking, and admin operations. " +
                   "Includes buyer/seller workflow management and comprehensive order lifecycle handling.")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;
    private final PayOsPaymentService payOsPaymentService;

    @Operation(
            summary = "Create a new order",
            description = "Creates a new order with the specified items and shipping address. Supports multiple payment methods including Stripe and COD. Returns payment client secret for Stripe payments."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Order created successfully",
                    content = @Content(schema = @Schema(implementation = OrderDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid order data or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - User role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> createOrder(
            @Parameter(description = "Order creation data including items and shipping address", required = true)
            @Valid @RequestBody OrderCreateRequest request,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        OrderDetailResponse order = orderService.createOrder(request, user.getUserId());
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<OrderDetailResponse>builder()
                        .message("Order created successfully")
                        .data(order)
                        .build());
    }

    @Operation(
            summary = "Create PayOS payment for order",
            description = "Creates a new order with PayOS payment method. Returns PayOS checkout URL for payment completion."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "PayOS payment created successfully",
                    content = @Content(schema = @Schema(implementation = PayOsPaymentService.PaymentResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid order data or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - User role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/payos")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PayOsPaymentService.PaymentResponse>> createPayOsPayment(
            @Parameter(description = "Order creation data for PayOS payment", required = true)
            @Valid @RequestBody OrderCreateRequest request,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        
        PayOsPaymentService.PaymentResponse paymentResponse = payOsPaymentService.createPayment(user.getUserId(), request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PayOsPaymentService.PaymentResponse>builder()
                        .message("PayOS payment created successfully")
                        .data(paymentResponse)
                        .build());
    }

    @Operation(
            summary = "Get user's orders with filtering",
            description = "Retrieve paginated list of orders for the authenticated user. Can filter by status and role (buyer/seller). Supports sorting and pagination."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Orders retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - User role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PagedResponse<OrderListResponse>>> getUserOrders(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Number of items per page", example = "10")
            @RequestParam(defaultValue = "10") int size,
            
            @Parameter(description = "Field to sort by", example = "createdAt",
                      schema = @Schema(allowableValues = {"createdAt", "totalAmount", "status"}))
            @RequestParam(defaultValue = "createdAt") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc",
                      schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(defaultValue = "desc") String sortDir,
            
            @Parameter(description = "Filter by order status",
                      schema = @Schema(allowableValues = {"PENDING", "PROCESSING", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED", "REFUNDED", "EXPIRED"}))
            @RequestParam(required = false) String status,
            
            @Parameter(description = "User role perspective", example = "buyer",
                      schema = @Schema(allowableValues = {"buyer", "seller"}))
            @RequestParam(defaultValue = "buyer") String role,
            
            @Parameter(hidden = true)
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

    @Operation(
            summary = "Get order details by ID",
            description = "Retrieve detailed information about a specific order. Only accessible by the buyer or sellers involved in the order."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Order retrieved successfully",
                    content = @Content(schema = @Schema(implementation = OrderDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Not authorized to view this order"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrderById(
            @Parameter(description = "Order ID", required = true, example = "1")
            @PathVariable Long orderId,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        
        // Service handles authorization validation and error handling
        OrderDetailResponse order = orderService.getOrderById(orderId, user.getUserId());
        
        return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                .message("Order retrieved successfully")
                .data(order)
                .build());    }

    @Operation(
            summary = "Confirm payment for an order",
            description = "Confirms payment for a pending order using Stripe payment intent. Transitions order to PROCESSING status and sets product status to SOLD."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Payment confirmed successfully",
                    content = @Content(schema = @Schema(implementation = OrderDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Payment verification failed or invalid order state"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Not the order buyer"
            )
    })    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/confirm-payment")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> confirmPayment(
            @Parameter(description = "Payment confirmation request containing order ID and Stripe payment intent ID")
            @Valid @RequestBody PaymentConfirmationRequest request,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        
        // Service handles payment verification and all business logic
        OrderDetailResponse order = orderService.confirmPayment(request.getOrderId(), user.getUserId(), request.getStripePaymentIntentId());
        
        return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                .message("Payment confirmed successfully")
                .data(order)
                .build());
    }

    @Operation(
            summary = "Update order item status",
            description = "Updates the status of a specific order item. Only buyers and sellers involved in the order can update item status based on role-specific permissions."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Order item status updated successfully",
                    content = @Content(schema = @Schema(implementation = OrderDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid status transition or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Not authorized for this status update"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{orderId}/items/{itemId}/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> updateOrderItemStatus(
            @Parameter(description = "Order ID", required = true, example = "1")
            @PathVariable Long orderId,
            
            @Parameter(description = "Order item ID", required = true, example = "1")
            @PathVariable Long itemId,
            
            @Parameter(description = "Status update request with new status and optional notes", required = true)
            @Valid @RequestBody OrderStatusUpdateRequest request,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        
        // Service handles all validation, authorization and business logic
        OrderDetailResponse order = orderService.updateOrderItemStatus(orderId, itemId, request, user.getUserId());
        
        return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                .message("Order item status updated successfully")
                .data(order)
                .build());
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
    }    // Endpoint for canceling an entire order has been removed as part of refactoring.
    // Cancellations now happen at the individual item level through the item status update endpoint.

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



    // ==================== ADMIN ENDPOINTS ====================

    @Operation(
            summary = "[ADMIN] Get all orders for a specific user",
            description = "Admin-only endpoint to retrieve all orders for a specific user with filtering and pagination. Allows admins to manage any user's orders."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "User orders retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/users/{userId}/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<OrderListResponse>>> getUserOrdersForAdmin(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable Long userId,
            
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Field to sort by", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDir,
            
            @Parameter(description = "Filter by order status")
            @RequestParam(required = false) String status,
            
            @Parameter(description = "User role perspective: buyer or seller", example = "buyer")
            @RequestParam(defaultValue = "buyer") String role,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Admin retrieving orders for user: {}", userId);
        
        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);
        PagedResponse<OrderListResponse> orders = orderService.getUserOrders(userId, status, role, pageable);
        
        return ResponseEntity.ok(ApiResponse.<PagedResponse<OrderListResponse>>builder()
                .message("User orders retrieved successfully by admin")
                .data(orders)
                .build());
    }

    @Operation(
            summary = "[ADMIN] Get all users with order summary",
            description = "Admin-only endpoint to get a list of all users with their order summary statistics. Useful for user management and monitoring."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Users with order summary retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/users/order-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<Object>>> getAllUsersWithOrderSummary(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Sort by: username, totalOrders, totalSpent, totalEarned, lastOrderDate")
            @RequestParam(defaultValue = "totalOrders") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDir,
            
            @Parameter(description = "Search by username or email")
            @RequestParam(required = false) String search,
            
            @Parameter(description = "Filter by user account status")
            @RequestParam(required = false) String accountStatus,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Admin retrieving all users with order summary");
        
        Pageable pageable = PaginationUtils.createPageable(page, size, sortBy, sortDir);
        PagedResponse<Object> usersWithOrders = orderService.getAllUsersWithOrderSummary(
                search, accountStatus, pageable);
        
        return ResponseEntity.ok(ApiResponse.<PagedResponse<Object>>builder()
                .message("Users with order summary retrieved successfully")
                .data(usersWithOrders)
                .build());
    }

    @Operation(
            summary = "[ADMIN] Force update any user's order",
            description = "Admin-only endpoint to force update any order for any user. Provides full admin control over order management."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Order updated successfully by admin"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid update request"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/admin/users/{userId}/orders/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> adminUpdateUserOrder(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable Long userId,
            
            @Parameter(description = "Order ID", required = true, example = "1")
            @PathVariable Long orderId,
            
            @Parameter(description = "New order status", required = true)
            @RequestParam String status,
            
            @Parameter(description = "Admin notes for the update")
            @RequestParam(required = false) String notes,
            
            @Parameter(description = "Force update even if business rules would normally prevent it")
            @RequestParam(defaultValue = "false") boolean forceUpdate,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User admin = userService.findByUsername(userDetails.getUsername());
        log.info("Admin {} updating order {} for user {}", admin.getUserId(), orderId, userId);
        
        OrderDetailResponse order = orderService.adminUpdateUserOrder(
                userId, orderId, status, notes, forceUpdate, admin.getUserId());
        
        return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                .message("Order updated successfully by admin")
                .data(order)
                .build());
    }

    @Operation(
            summary = "[ADMIN] Get all orders with comprehensive filtering",
            description = "Admin-only endpoint to retrieve all orders in the system with advanced filtering options including status, buyer, seller, date range, and search functionality."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Orders retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<OrderListResponse>>> getAllOrdersForAdmin(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Field to sort by", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            
            @Parameter(description = "Sort direction", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDir,
            
            @Parameter(description = "Filter by order status",
                      schema = @Schema(allowableValues = {"PENDING", "PROCESSING", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED", "REFUNDED", "EXPIRED"}))
            @RequestParam(required = false) String status,
            
            @Parameter(description = "Filter by buyer ID")
            @RequestParam(required = false) Long buyerId,
            
            @Parameter(description = "Filter by seller ID")
            @RequestParam(required = false) Long sellerId,
            
            @Parameter(description = "Search in buyer username or order ID")
            @RequestParam(required = false) String search,
            
            @Parameter(description = "Start date for filtering (ISO format)", example = "2024-01-01T00:00:00")
            @RequestParam(required = false) String startDate,
            
            @Parameter(description = "End date for filtering (ISO format)", example = "2024-12-31T23:59:59")
            @RequestParam(required = false) String endDate) {        return ResponseEntity.ok(ApiResponse.<PagedResponse<OrderListResponse>>builder()
                .message("All orders retrieved successfully")
                .data(orderService.getAllOrdersForAdmin(
                        status, buyerId, sellerId, search, 
                        orderService.parseDateTime(startDate), orderService.parseDateTime(endDate), 
                        PaginationUtils.createPageable(page, size, sortBy, sortDir)))
                .build());
    }

    @Operation(
            summary = "[ADMIN] Force update order status",
            description = "Admin-only endpoint to force change order status regardless of current state. Useful for handling edge cases and manual intervention."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Order status updated successfully",
                    content = @Content(schema = @Schema(implementation = OrderDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid status or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/admin/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> forceUpdateOrderStatus(
            @Parameter(description = "Order ID", required = true, example = "1")
            @PathVariable Long orderId,
            
            @Parameter(description = "New order status", required = true)
            @RequestParam String status,
            
            @Parameter(description = "Admin notes for the status change")
            @RequestParam(required = false) String notes,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User admin = userService.findByUsername(userDetails.getUsername());
        OrderDetailResponse order = orderService.forceUpdateOrderStatus(orderId, status, notes, admin.getUserId());
        
        return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                .message("Order status updated successfully by admin")
                .data(order)
                .build());
    }

    @Operation(
            summary = "[ADMIN] Process order refund",
            description = "Admin-only endpoint to process refunds for any order. Handles both Stripe refunds and manual refund processing."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Refund processed successfully",
                    content = @Content(schema = @Schema(implementation = OrderDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Refund processing failed"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/admin/{orderId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> processRefund(
            @Parameter(description = "Order ID", required = true, example = "1")
            @PathVariable Long orderId,
            
            @Parameter(description = "Refund amount", required = true, example = "150000")
            @RequestParam String refundAmount,
            
            @Parameter(description = "Reason for refund", required = true)
            @RequestParam String reason,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {        User admin = userService.findByUsername(userDetails.getUsername());
        
        // Parse refund amount - handle potential parsing errors in service layer
        BigDecimal parsedRefundAmount;
        try {
            parsedRefundAmount = new BigDecimal(refundAmount);
        } catch (NumberFormatException e) {
            throw new BusinessLogicException("Invalid refund amount format: " + refundAmount);
        }
        
        OrderDetailResponse order = orderService.processAdminRefund(
                orderId, parsedRefundAmount, reason, admin.getUserId());
        
        return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                .message("Refund processed successfully by admin")
                .data(order)
                .build());
    }

    @Operation(
            summary = "[ADMIN] Reconcile stuck orders",
            description = "Admin-only endpoint to trigger reconciliation of pending orders that may be stuck due to payment processing issues."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Reconciliation triggered successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error during reconciliation"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/admin/reconcile-stuck-orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> triggerReconciliation(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User admin = userService.findByUsername(userDetails.getUsername());
        orderService.reconcilePendingOrders();
        
        log.info("Order reconciliation triggered by admin: {}", admin.getUserId());
        
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .message("Reconciliation job triggered successfully by admin")
                .build());
    }

    @Operation(
            summary = "Check payment status",
            description = "Check the current status of a payment for debugging purposes"
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{orderId}/payment-status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkPaymentStatus(
            @Parameter(description = "Order ID", required = true, example = "1")
            @PathVariable Long orderId,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        
        try {
            // Get order and validate user access
            OrderDetailResponse order = orderService.getOrderById(orderId, user.getUserId());
            
            if (order.getStripePaymentIntentId() == null) {
                return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                        .message("No payment intent found for this order")
                        .data(Map.of("status", "no_payment_intent"))
                        .build());
            }
            
            // Check current payment status
            String paymentStatus = orderService.checkPaymentStatus(order.getStripePaymentIntentId());
            
            Map<String, Object> statusData = Map.of(
                "paymentIntentId", order.getStripePaymentIntentId(),
                "status", paymentStatus,
                "orderStatus", order.getStatus()
            );
            
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .message("Payment status retrieved")
                    .data(statusData)
                    .build());
                    
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<Map<String, Object>>builder()
                            .message("Failed to check payment status: " + e.getMessage())
                            .data(null)
                            .build());
        }
    }

    @Operation(
            summary = "Admin: Trigger cleanup of expired orders",
            description = "Manually trigger cleanup of expired orders (normally runs automatically every 15 minutes)"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/admin/cleanup-expired")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerExpiredOrderCleanup() {
        try {
            LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
            
            List<se.vestige_be.pojo.Order> expiredOrders = orderService.findExpiredOrders(thirtyMinutesAgo);
            int cleanedCount = orderService.cleanupExpiredOrdersManually();
            
            Map<String, Object> result = Map.of(
                "foundExpiredOrders", expiredOrders.size(),
                "cleanedUpOrders", cleanedCount,
                "cutoffTime", thirtyMinutesAgo.toString()
            );
            
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .message("Expired order cleanup completed")
                    .data(result)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Map<String, Object>>builder()
                            .message("Failed to cleanup expired orders: " + e.getMessage())
                            .data(null)
                            .build());
        }
    }

    @Operation(
            summary = "Clean up failed payments",
            description = "Admin endpoint to manually clean up orders with failed payments"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Failed payments cleanup completed successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - admin role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error during cleanup"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/admin/cleanup-failed-payments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> cleanupFailedPayments(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User admin = userService.findByUsername(userDetails.getUsername());
        orderService.cleanupExpiredPendingOrders();
        
        log.info("Failed payments cleanup triggered by admin: {}", admin.getUserId());
        
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .message("Failed payments cleanup completed successfully by admin")
                .build());
    }

    // ==================== ADMIN DASHBOARD ENDPOINTS ====================

    @Operation(
            summary = "[ADMIN] Get system-wide order statistics",
            description = "Get comprehensive statistics for admin dashboard including order counts, revenue, platform fees, and breakdowns by status."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Statistics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            )
    })
    @GetMapping("/admin/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getSystemOrderStats() {
        log.info("Admin retrieving system order statistics");
        
        Object stats = orderService.getSystemOrderStats();
        
        return ResponseEntity.ok(ApiResponse.builder()
                .message("System order statistics retrieved successfully")
                .data(stats)
                .build());
    }

    @Operation(
            summary = "[ADMIN] Get comprehensive system statistics",
            description = "Enhanced admin endpoint for comprehensive system statistics including user metrics, order trends, financial overview, and performance indicators."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Comprehensive statistics retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Admin role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/comprehensive-statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getComprehensiveSystemStatistics(
            @Parameter(description = "Period for trends: daily, weekly, monthly", example = "monthly")
            @RequestParam(defaultValue = "monthly") String period,
            
            @Parameter(description = "Number of periods to include for trends", example = "12")
            @RequestParam(defaultValue = "12") int periods,
            
            @Parameter(description = "Start date for filtering (ISO format)")
            @RequestParam(required = false) String startDate,
            
            @Parameter(description = "End date for filtering (ISO format)")
            @RequestParam(required = false) String endDate,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Admin retrieving comprehensive system statistics");
        
        LocalDateTime start = startDate != null ? orderService.parseDateTime(startDate) : null;
        LocalDateTime end = endDate != null ? orderService.parseDateTime(endDate) : null;
        
        Object stats = orderService.getComprehensiveSystemStatistics(period, periods, start, end);
        
        return ResponseEntity.ok(ApiResponse.builder()
                .message("Comprehensive system statistics retrieved successfully")
                .data(stats)
                .build());
    }

    @Operation(
            summary = "[ADMIN] Get all transactions with filtering",
            description = "Get paginated list of all transactions with comprehensive filtering options for admin dashboard."
    )
    @GetMapping("/admin/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<Object>>> getAllTransactions(
            @Parameter(description = "Transaction status filter") @RequestParam(required = false) String status,
            @Parameter(description = "Escrow status filter") @RequestParam(required = false) String escrowStatus,
            @Parameter(description = "Buyer ID filter") @RequestParam(required = false) Long buyerId,
            @Parameter(description = "Seller ID filter") @RequestParam(required = false) Long sellerId,
            @Parameter(description = "Start date (YYYY-MM-DD)") @RequestParam(required = false) String startDate,
            @Parameter(description = "End date (YYYY-MM-DD)") @RequestParam(required = false) String endDate,
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            Pageable pageable
    ) {
        log.info("Admin retrieving all transactions with filters");
        
        LocalDateTime start = startDate != null ? orderService.parseDateTime(startDate) : null;
        LocalDateTime end = endDate != null ? orderService.parseDateTime(endDate) : null;
        
        PagedResponse<Object> transactions = orderService.getAllTransactionsForAdmin(
                status, escrowStatus, buyerId, sellerId, start, end, search, pageable);
        
        return ResponseEntity.ok(ApiResponse.<PagedResponse<Object>>builder()
                .message("Transactions retrieved successfully")
                .data(transactions)
                .build());
    }

    @Operation(
            summary = "[ADMIN] Get problem transactions",
            description = "Get transactions that require admin attention (disputes, failed transfers, stuck escrows, etc.)"
    )
    @GetMapping("/admin/transactions/problems")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getProblemTransactions() {
        log.info("Admin retrieving problem transactions");
        
        Object problemTransactions = orderService.getProblemTransactions();
        
        return ResponseEntity.ok(ApiResponse.builder()
                .message("Problem transactions retrieved successfully")
                .data(problemTransactions)
                .build());
    }

    @Operation(
            summary = "[ADMIN] Force release escrow",
            description = "Manually release funds from escrow to seller for a specific transaction."
    )
    @PostMapping("/admin/transactions/{transactionId}/release-escrow")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> forceReleaseEscrow(
            @Parameter(description = "Transaction ID") @PathVariable Long transactionId,
            @Parameter(description = "Admin notes for the action") @RequestParam(required = false) String notes,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("Admin force releasing escrow for transaction: {}", transactionId);
        
        User admin = userService.findByUsername(userDetails.getUsername());
        orderService.forceReleaseEscrow(transactionId, notes, admin.getUserId());
        
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .message("Escrow released successfully by admin")
                .build());
    }

    @Operation(
            summary = "[ADMIN] Update escrow status",
            description = "Manually update escrow status for a specific transaction. Supports manual handling of PayOS payments."
    )
    @PutMapping("/admin/transactions/{transactionId}/escrow-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> updateEscrowStatus(
            @Parameter(description = "Transaction ID") @PathVariable Long transactionId,
            @Parameter(description = "New escrow status") @RequestParam String escrowStatus,
            @Parameter(description = "Admin notes for the action") @RequestParam(required = false) String notes,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("Admin updating escrow status for transaction: {} to {}", transactionId, escrowStatus);
        
        User admin = userService.findByUsername(userDetails.getUsername());
        orderService.updateEscrowStatus(transactionId, escrowStatus, notes, admin.getUserId());
        
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .message("Escrow status updated successfully by admin")
                .build());
    }
    @Operation(
            summary = "[ADMIN] Get transactions awaiting escrow release",
            description = "Get all transactions in AWAITING_RELEASE status that need admin attention for escrow release."
    )
    @GetMapping("/admin/transactions/awaiting-release")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTransactionsAwaitingRelease() {
        
        List<Map<String, Object>> transactions = orderService.getTransactionsAwaitingRelease();
        
        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .message("Transactions awaiting escrow release retrieved successfully")
                .data(transactions)
                .build());
    }

    @Operation(
            summary = "Request pickup for a packed item",
            description = "Allows a seller to request pickup from Vestige Shipping for a packed item. Transitions order item status to AWAITING_PICKUP."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Pickup requested successfully",
                    content = @Content(schema = @Schema(implementation = OrderDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid item status or business rule violation"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - Must be the seller of the item"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{orderId}/items/{itemId}/request-pickup")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> requestItemPickup(
            @Parameter(description = "Order ID", required = true, example = "1")
            @PathVariable Long orderId,
            
            @Parameter(description = "Order item ID", required = true, example = "1")
            @PathVariable Long itemId,
            
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        OrderDetailResponse order = orderService.requestItemPickup(orderId, itemId, user.getUserId());

        return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                .message("Pickup requested. Our shipper will arrive shortly.")
                .data(order)
                .build());
    }
}