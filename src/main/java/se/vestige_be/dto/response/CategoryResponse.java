package se.vestige_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import se.vestige_be.pojo.Category;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private Long categoryId;
    private String name;
    private String description;
    private LocalDateTime createdAt;

    private CategoryParentResponse parent;

    private List<CategoryResponse> children;

    private Integer level;
    private boolean hasChildren;
    private Integer childrenCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryParentResponse {
        private Long categoryId;
        private String name;

        public static CategoryParentResponse fromEntity(Category category) {
            return CategoryParentResponse.builder()
                    .categoryId(category.getCategoryId())
                    .name(category.getName())
                    .build();
        }
    }    public static CategoryResponse fromEntity(Category category, Integer level) {
        // Safely access lazy-loaded collections
        List<CategoryResponse> children = null;
        boolean hasChildren = false;
        int childrenCount = 0;
        
        try {
            if (category.getSubcategories() != null) {
                children = category.getSubcategories().stream()
                        .map(child -> CategoryResponse.fromEntity(child, level + 1))
                        .collect(Collectors.toList());
                hasChildren = !category.getSubcategories().isEmpty();
                childrenCount = category.getSubcategories().size();
            }
        } catch (Exception e) {
            // Handle lazy loading exception gracefully
            children = List.of();
            hasChildren = false;
            childrenCount = 0;
        }

        CategoryParentResponse parent = null;
        try {
            if (category.getParentCategory() != null) {
                parent = CategoryParentResponse.fromEntity(category.getParentCategory());
            }
        } catch (Exception e) {
            // Handle lazy loading exception gracefully
            parent = null;
        }

        return CategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .description(category.getDescription())
                .createdAt(category.getCreatedAt())
                .parent(parent)
                .children(children)
                .hasChildren(hasChildren)
                .childrenCount(childrenCount)
                .level(level)
                .build();
    }
    public static List<CategoryResponse> fromEntityList(List<Category> categories) {
        return categories.stream()
                .map(category -> CategoryResponse.fromEntity(category, 0))
                .collect(Collectors.toList());
    }

}
