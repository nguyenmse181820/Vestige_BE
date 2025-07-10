package se.vestige_be.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPickupRequest {
    
    @NotNull(message = "Transaction ID is required")
    private Long transactionId;
    
    @NotEmpty(message = "At least one photo URL is required.")
    @Size(min = 1, max = 10, message = "Must provide between 1 and 10 photo URLs")
    private List<@Size(max = 512, message = "Photo URL must not exceed 512 characters") String> photoUrls;
}
