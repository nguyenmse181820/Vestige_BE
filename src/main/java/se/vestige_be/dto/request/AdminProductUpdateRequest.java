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
public class AdminProductUpdateRequest {
    @Size(min = 5, max = 100, message = "Title must be between 5 and 100 characters")
    private String title;

    @Size(min = 10, max = 5000, message = "Description must be between 10 and 5000 characters")
    private String description;

    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @DecimalMax(value = "99999999.99", message = "Price cannot exceed 99,999,999.99 VND")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "Original price must be greater than 0")
    @DecimalMax(value = "99999999.99", message = "Original price cannot exceed 99,999,999.99 VND")
    private BigDecimal originalPrice;

    private ProductCondition condition;

    @Size(max = 20, message = "Size cannot exceed 20 characters")
    private String size;

    @Size(max = 50, message = "Color cannot exceed 50 characters")
    private String color;

    // Admin can change any status including SOLD, INACTIVE, REPORTED, etc.
    private ProductStatus status;
    
    private Long categoryId;
    private Long brandId;
    private List<String> imageUrls;

    // Admin-specific fields
    @Size(max = 1000, message = "Admin notes cannot exceed 1000 characters")
    private String adminNotes;

    // Allow admin to change seller (transfer ownership)
    private Long sellerId;

    // Admin can override sold status and date
    private Boolean forceSoldStatus;

    // Helper methods
    public boolean hasTitle() { return title != null; }
    public boolean hasDescription() { return description != null; }
    public boolean hasPrice() { return price != null; }
    public boolean hasOriginalPrice() { return originalPrice != null; }
    public boolean hasCondition() { return condition != null; }
    public boolean hasSize() { return size != null; }
    public boolean hasColor() { return color != null; }
    public boolean hasStatus() { return status != null; }
    public boolean hasCategoryId() { return categoryId != null; }
    public boolean hasBrandId() { return brandId != null; }
    public boolean hasImageUrls() { return imageUrls != null && !imageUrls.isEmpty(); }
    public boolean hasAdminNotes() { return adminNotes != null; }
    public boolean hasSellerId() { return sellerId != null; }
    public boolean hasForceSoldStatus() { return forceSoldStatus != null; }
}
