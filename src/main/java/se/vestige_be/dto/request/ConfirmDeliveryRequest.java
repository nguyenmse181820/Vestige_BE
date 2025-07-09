package se.vestige_be.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class ConfirmDeliveryRequest {
    @NotEmpty(message = "At least one photo URL is required as proof of delivery.")
    private List<@Size(max = 512) String> photoUrls;
}
