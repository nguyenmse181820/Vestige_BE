package se.vestige_be.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

@Data
@NoArgsConstructor
public class DocumentRequestDto {
    
    @NotBlank(message = "Document URL cannot be blank")
    @URL(message = "Must be a valid URL")
    private String url;

    @NotBlank(message = "Document type cannot be blank")
    private String type;
}
