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
    }

    public static CategoryResponse fromEntity(Category category, Integer level) {
        return CategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .description(category.getDescription())
                .createdAt(category.getCreatedAt())
                .parent(category.getParentCategory() != null ?
                        CategoryParentResponse.fromEntity(category.getParentCategory()) : null)
                .children(category.getSubcategories().stream()
                        .map(child -> CategoryResponse.fromEntity(child, level + 1))
                        .collect(Collectors.toList()))
                .hasChildren(!category.getSubcategories().isEmpty())
                .childrenCount(category.getSubcategories().size())
                .level(level)
                .build();
    }
    public static List<CategoryResponse> fromEntityList(List<Category> categories) {
        return categories.stream()
                .map(category -> CategoryResponse.fromEntity(category, 0))
                .collect(Collectors.toList());
    }

}
