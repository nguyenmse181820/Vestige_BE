package se.vestige_be.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.response.ApiResponse;
import se.vestige_be.dto.response.ProductFilterResponse;
import se.vestige_be.dto.response.ProductListResponse;
import se.vestige_be.service.ProductService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductListResponse>>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long sellerId) {

        ProductFilterResponse filterDto = ProductFilterResponse.builder()
                .search(search)
                .categoryId(categoryId)
                .brandId(brandId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .condition(condition)
                .status(status != null ? status : "ACTIVE")
                .sellerId(sellerId)
                .build();

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(sortDir.equalsIgnoreCase("desc") ?
                        Sort.Direction.DESC : Sort.Direction.ASC, sortBy));

        Page<ProductListResponse> products = productService.getProducts(filterDto, pageable);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("totalElements", products.getTotalElements());
        metadata.put("totalPages", products.getTotalPages());
        metadata.put("currentPage", products.getNumber());
        metadata.put("pageSize", products.getSize());

        if (search != null) metadata.put("searchTerm", search);
        if (categoryId != null) metadata.put("categoryFilter", categoryId);
        if (brandId != null) metadata.put("brandFilter", brandId);
        if (minPrice != null || maxPrice != null) {
            metadata.put("priceFilter", Map.of(
                    "min", minPrice != null ? minPrice : "0",
                    "max", maxPrice != null ? maxPrice : "unlimited"
            ));
        }

        return ResponseEntity.ok(ApiResponse.<List<ProductListResponse>>builder()
                .status(HttpStatus.OK.toString())
                .message("Products retrieved successfully")
                .data(products.getContent())
                .metadata(metadata)
                .build());
    }
}
