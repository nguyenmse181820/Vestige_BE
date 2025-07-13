package se.vestige_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a review for a seller after purchase")
public class CreateReviewRequest {

    @NotNull(message = "Transaction ID is required")
    @Schema(description = "Transaction ID to review", example = "1")
    private Long transactionId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    @Schema(description = "Overall rating for the seller (1-5 stars)", example = "5")
    private Integer rating;

    @Size(max = 1000, message = "Comment cannot exceed 1000 characters")
    @Schema(description = "Optional comment about the seller", example = "Great seller, fast shipping and item as described!")
    private String comment;

    @Min(value = 1, message = "Authenticity rating must be between 1 and 5")
    @Max(value = 5, message = "Authenticity rating must be between 1 and 5")
    @Schema(description = "Optional authenticity rating for the product (1-5 stars)", example = "5")
    private Integer authenticityRating;

    @Size(max = 500, message = "Authenticity comment cannot exceed 500 characters")
    @Schema(description = "Optional comment about product authenticity", example = "Product appears authentic and in excellent condition")
    private String authenticityComment;
}
