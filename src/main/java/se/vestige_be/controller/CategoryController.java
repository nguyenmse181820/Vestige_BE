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
import se.vestige_be.dto.request.CategoryRequest;
import se.vestige_be.dto.response.ObjectResponse;
import se.vestige_be.service.CategoryService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Category", description = "API for category management")
@AllArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Get all categories", description = "Public endpoint to retrieve all categories")
    public ResponseEntity<?> getCategories() {
        return ResponseEntity.status(HttpStatus.OK).body(
                ObjectResponse.builder()
                        .status(HttpStatus.OK.toString())
                        .message("Successfully retrieved categories")
                        .data(categoryService.findAll())
                        .build()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID", description = "Public endpoint to retrieve a specific category")
    public ResponseEntity<?> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(
                ObjectResponse.builder()
                        .status(HttpStatus.OK.toString())
                        .message("Successfully retrieved category")
                        .data(categoryService.getCategoryById(id))
                        .build()
        );
    }

    @PostMapping()
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create new category", description = "Admin-only endpoint to create a new category")
    @SecurityRequirement(name = "bearer-auth")
    public ResponseEntity<?> createNewCategory(
            @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ObjectResponse.builder()
                        .status(HttpStatus.CREATED.toString())
                        .message("Category created")
                        .data(categoryService.createCategory(
                                request.getName(),
                                request.getDescription(),
                                request.getParentCategoryId()
                        ))
                        .build()
        );
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update category", description = "Admin-only endpoint to update an existing category")
    @SecurityRequirement(name = "bearer-auth")
    public ResponseEntity<?> updateCategory(@Valid @PathVariable Long id, @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .data(categoryService.updateCategory(
                                id,
                                request.getName(),
                                request.getDescription(),
                                request.getParentCategoryId()))
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete category", description = "Admin-only endpoint to delete a category")
    @SecurityRequirement(name = "bearer-auth")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status(HttpStatus.OK.toString())
                        .message("Category deleted successfully")
                        .data(null)
                        .build()
        );
    }

    @GetMapping("/{id}/subcategories")
    @Operation(summary = "Get all subcategory IDs for a category", 
               description = "Get all subcategory IDs including the category itself and all its descendants. Useful for hierarchical filtering.")
    public ResponseEntity<?> getCategoryWithSubcategories(@PathVariable Long id) {
        try {
            List<Long> categoryIds = categoryService.getCategoryWithAllSubcategoryIds(id);
            return ResponseEntity.ok(
                    ObjectResponse.builder()
                            .status(HttpStatus.OK.toString())
                            .message("Successfully retrieved category hierarchy")
                            .data(Map.of(
                                    "categoryId", id,
                                    "allCategoryIds", categoryIds,
                                    "hasSubcategories", categoryIds.size() > 1
                            ))
                            .build()
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ObjectResponse.builder()
                            .status(HttpStatus.NOT_FOUND.toString())
                            .message("Category not found")
                            .data(null)
                            .build()
            );
        }
    }

    @GetMapping("/{id}/tree")
    @Operation(summary = "Get category tree", 
               description = "Get the complete category tree starting from the specified category")
    public ResponseEntity<?> getCategoryTree(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(
                    ObjectResponse.builder()
                            .status(HttpStatus.OK.toString())
                            .message("Successfully retrieved category tree")
                            .data(categoryService.getCategoryTree(id))
                            .build()
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ObjectResponse.builder()
                            .status(HttpStatus.NOT_FOUND.toString())
                            .message("Category not found")
                            .data(null)
                            .build()
            );
        }
    }

    @GetMapping("/{id}/has-subcategories")
    @Operation(summary = "Check if category has subcategories", 
               description = "Check if the specified category has any subcategories")
    public ResponseEntity<?> hasSubcategories(@PathVariable Long id) {
        boolean hasSubcategories = categoryService.hasSubcategories(id);
        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status(HttpStatus.OK.toString())
                        .message("Successfully checked category subcategories")
                        .data(Map.of(
                                "categoryId", id,
                                "hasSubcategories", hasSubcategories
                        ))
                        .build()
        );
    }

}