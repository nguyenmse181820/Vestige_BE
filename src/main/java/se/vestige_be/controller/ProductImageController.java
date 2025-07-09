package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.ProductImageRequest;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.service.ProductService;
import se.vestige_be.pojo.User;
import se.vestige_be.service.UserService;

@RestController
@RequestMapping("/api/products/{productId}/images")
@RequiredArgsConstructor
public class ProductImageController {
    private final ProductService productService;
    private final UserService userService;

    @Operation(summary = "Delete product image", description = "Delete an image from a product. Only the product owner or admin can delete.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername());
        productService.deleteProductImage(productId, imageId, user);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.toString())
                .message("Image deleted successfully.")
                .build());
    }
}
