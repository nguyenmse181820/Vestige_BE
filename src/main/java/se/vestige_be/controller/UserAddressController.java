package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.UserAddressRequest;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.dto.response.UserAddressResponse;
import se.vestige_be.pojo.User;
import se.vestige_be.service.UserAddressService;
import se.vestige_be.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/users/addresses")
@Tag(name = "User Address", description = "API for user address management")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService userAddressService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<UserAddressResponse>>> getUserAddresses(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());
        List<UserAddressResponse> addresses = userAddressService.getUserAddresses(user.getUserId());

        return ResponseEntity.ok(ApiResponse.<List<UserAddressResponse>>builder()
                .message("Addresses retrieved successfully")
                .data(addresses)
                .build());
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<UserAddressResponse>> createAddress(
            @Valid @RequestBody UserAddressRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            UserAddressResponse address = userAddressService.createAddress(request, user.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.<UserAddressResponse>builder()
                            .message("Address created successfully")
                            .data(address)
                            .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<UserAddressResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/{addressId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<UserAddressResponse>> getAddressById(
            @PathVariable Long addressId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            UserAddressResponse address = userAddressService.getAddressById(addressId, user.getUserId());
            return ResponseEntity.ok(ApiResponse.<UserAddressResponse>builder()
                    .message("Address retrieved successfully")
                    .data(address)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<UserAddressResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @PatchMapping("/{addressId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<UserAddressResponse>> updateAddress(
            @PathVariable Long addressId,
            @Valid @RequestBody UserAddressRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            UserAddressResponse address = userAddressService.updateAddress(addressId, request, user.getUserId());
            return ResponseEntity.ok(ApiResponse.<UserAddressResponse>builder()
                    .message("Address updated successfully")
                    .data(address)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<UserAddressResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @DeleteMapping("/{addressId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable Long addressId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            userAddressService.deleteAddress(addressId, user.getUserId());
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .message("Address deleted successfully")
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<Void>builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/{addressId}/set-default")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<UserAddressResponse>> setDefaultAddress(
            @PathVariable Long addressId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findByUsername(userDetails.getUsername());

        try {
            UserAddressResponse address = userAddressService.setDefaultAddress(addressId, user.getUserId());
            return ResponseEntity.ok(ApiResponse.<UserAddressResponse>builder()
                    .message("Default address updated successfully")
                    .data(address)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<UserAddressResponse>builder()
                            .message(e.getMessage())
                            .build());
        }
    }
}