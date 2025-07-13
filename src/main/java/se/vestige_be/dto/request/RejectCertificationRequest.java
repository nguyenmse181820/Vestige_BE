package se.vestige_be.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
public class RejectCertificationRequest {
    
    @NotBlank(message = "Rejection reason cannot be blank")
    private String reason;
}
