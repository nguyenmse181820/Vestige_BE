package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.CategoryRequest;
import se.vestige_be.dto.response.ObjectResponse;
import se.vestige_be.service.CategoryService;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Category", description = "API for category management")
@AllArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<?> getCategories() {
        return ResponseEntity.ok(categoryService.findAll());
    }

    @PostMapping()
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
    public ResponseEntity<?> updateCategory(@Valid @PathVariable Integer id, @RequestBody CategoryRequest request) {
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
    public ResponseEntity<?> deleteCategory(@PathVariable Integer id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status(HttpStatus.OK.toString())
                        .message("Category deleted successfully")
                        .data(null)
                        .build()
        );
    }

}
