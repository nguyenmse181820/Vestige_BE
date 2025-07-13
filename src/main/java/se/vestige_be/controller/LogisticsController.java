package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.ConfirmPickupRequest;
import se.vestige_be.dto.request.ConfirmDeliveryRequest;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.dto.response.OrderDetailResponse;
import se.vestige_be.dto.response.PickupItemResponse;

import jakarta.validation.Valid;
import se.vestige_be.service.LogisticsService;

import java.util.List;

@RestController
@RequestMapping("/api/logistics")
@Tag(name = "Logistics Management", 
     description = "Internal logistics operations for Vestige Shipping team. " +
                   "Handles pickup confirmations, warehouse operations, and delivery management.")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('SHIPPER')")
public class LogisticsController {

    private final LogisticsService logisticsService;

    @Operation(
            summary = "Get items awaiting pickup",
            description = "Retrieve all order items that are currently awaiting pickup by the Vestige Shipping team."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Items retrieved successfully",
                    content = @Content(schema = @Schema(implementation = List.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - SHIPPER role required"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/pickups")
    public ResponseEntity<ApiResponse<List<PickupItemResponse>>> getItemsAwaitingPickup() {
        List<PickupItemResponse> items = logisticsService.getItemsAwaitingPickup();
        
        return ResponseEntity.ok(ApiResponse.<List<PickupItemResponse>>builder()
                .message("Items awaiting pickup retrieved successfully")
                .data(items)
                .build());
    }

    @Operation(
            summary = "Confirm package pickup with evidence",
            description = "Confirm that a package has been picked up and is now in the warehouse with photographic evidence. " +
                         "Accepts pre-uploaded image URLs as proof of condition during pickup."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Pickup confirmed successfully with evidence stored",
                    content = @Content(schema = @Schema(implementation = OrderDetailResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid pickup confirmation request or evidence"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Access denied - SHIPPER role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Transaction not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/confirm-pickup")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> confirmPickup(
            @Valid @RequestBody ConfirmPickupRequest request) {
        
        OrderDetailResponse order = logisticsService.confirmPickup(request);
        
        return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                .message("Package pickup confirmed successfully with evidence stored")
                .data(order)
                .build());
    }

    @Operation(
            summary = "Dispatch item for delivery",
            description = "Mark an item as out for delivery to the buyer's address."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Item dispatched successfully",
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
                    description = "Access denied - SHIPPER role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Order item not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/items/{itemId}/dispatch")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> dispatchItem(
            @Parameter(description = "Order item ID", required = true, example = "1")
            @PathVariable Long itemId) {
        
        OrderDetailResponse order = logisticsService.dispatchItem(itemId);
        
        return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                .message("Item dispatched for delivery successfully")
                .data(order)
                .build());
    }

    @Operation(
            summary = "Confirm final delivery with photo evidence",
            description = "Confirm that an item has been successfully delivered to the buyer. Requires photo evidence and triggers escrow release."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Delivery confirmed successfully with photo evidence",
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
                    description = "Access denied - SHIPPER role required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Order item not found"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/items/{itemId}/confirm-delivery")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> confirmDelivery(
            @Parameter(description = "Order item ID", required = true, example = "1")
            @PathVariable Long itemId,
            @RequestBody ConfirmDeliveryRequest request) {

        OrderDetailResponse order = logisticsService.confirmDelivery(itemId, request.getPhotoUrls());

        return ResponseEntity.ok(ApiResponse.<OrderDetailResponse>builder()
                .message("Delivery confirmed successfully with " + request.getPhotoUrls().size() + " evidence photos.")
                .data(order)
                .build());
    }
}
