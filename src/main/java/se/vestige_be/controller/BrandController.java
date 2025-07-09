package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.BrandRequest;
import se.vestige_be.dto.response.BrandResponse;
import se.vestige_be.dto.response.ObjectResponse;
import se.vestige_be.service.BrandService;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
@AllArgsConstructor
@Tag(name = "Brand", description = "API for brand management")
public class BrandController {
    private final BrandService brandService;

    @GetMapping
    @Operation(summary = "Get all brands", description = "Public endpoint to retrieve all brands")
    public ResponseEntity<?> getAllBrands() {
        List<BrandResponse> brandDTOs = brandService.findAll();
        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status(HttpStatus.OK.toString())
                        .message("Successfully retrieved brands")
                        .data(brandDTOs)
                        .build()
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create new brand", description = "Admin-only endpoint to create a new brand")
    @SecurityRequirement(name = "bearer-auth")
    public ResponseEntity<?> createBrand(@Valid @RequestBody BrandRequest brandRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ObjectResponse.builder()
                        .status(HttpStatus.CREATED.toString())
                        .message("Successfully created brand")
                        .data(brandService.createBrand(brandRequest.getName(), brandRequest.getLogoUrl()))
                        .build()
        );
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update brand", description = "Admin-only endpoint to update an existing brand")
    @SecurityRequirement(name = "bearer-auth")
    public ResponseEntity<?> updateBrand(@Valid @PathVariable Long id, @RequestBody BrandRequest request) {
        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status(HttpStatus.OK.toString())
                        .message("Successfully updated brand")
                        .data(brandService.updateBrand(id, request.getName(), request.getLogoUrl()))
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete brand", description = "Admin-only endpoint to delete a brand")
    @SecurityRequirement(name = "bearer-auth")
    public ResponseEntity<?> deleteBrand(@PathVariable Long id) {
        brandService.deleteBrand(id);
        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status(HttpStatus.OK.toString())
                        .message("Brand deleted successfully")
                        .data(null)
                        .build()
        );
    }
}
