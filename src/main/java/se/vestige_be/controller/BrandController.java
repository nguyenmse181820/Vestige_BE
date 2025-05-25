package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.BrandRequest;
import se.vestige_be.dto.response.ObjectResponse;
import se.vestige_be.service.BrandService;

@RestController
@RequestMapping("/api/brands")
@AllArgsConstructor
@Tag(name = "Brand", description = "API for brand management")
public class BrandController {
    private final BrandService brandService;

    @GetMapping
    public ResponseEntity<?> getAllBrands() {
        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status(HttpStatus.OK.toString())
                        .message("Successfully retrieved brands")
                        .data(brandService.findAll())
                        .build()
        );
    }

    @PostMapping
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
