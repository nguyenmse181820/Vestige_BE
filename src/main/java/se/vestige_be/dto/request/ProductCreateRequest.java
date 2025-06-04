package se.vestige_be.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import se.vestige_be.pojo.enums.ProductCondition;
import se.vestige_be.pojo.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateRequest {
    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 100, message = "Title must be between 5 and 100 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 5000, message = "Description must be between 10 and 5000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @DecimalMax(value = "99999999.99", message = "Price cannot exceed 99,999,999.99 VND")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "Original price must be greater than 0")
    @DecimalMax(value = "99999999.99", message = "Original price cannot exceed 99,999,999.99 VND")
    private BigDecimal originalPrice;

    @NotNull(message = "Condition is required")
    private ProductCondition condition;

    @Size(max = 20, message = "Size cannot exceed 20 characters")
    private String size;

    @Size(max = 50, message = "Color cannot exceed 50 characters")
    private String color;

    private ProductStatus status;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotNull(message = "Brand ID is required")
    private Long brandId;

    @NotNull(message = "At least one image is required")
    @Size(min = 1, max = 10, message = "Must provide between 1 and 10 images")
    private List<String> imageUrls;
}
