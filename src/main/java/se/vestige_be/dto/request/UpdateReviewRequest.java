package se.vestige_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update a review for a seller after purchase")
public class UpdateReviewRequest {

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
}