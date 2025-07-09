package se.vestige_be.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import se.vestige_be.pojo.enums.Gender;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, numbers, dots, underscores and hyphens")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one number and one special character")
    private String password;

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
    @Pattern(regexp = "^[a-zA-ZÀ-ÿ\\s'-]+$", message = "First name can only contain letters, spaces, apostrophes and hyphens")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
    @Pattern(regexp = "^[a-zA-ZÀ-ÿ\\s'-]+$", message = "Last name can only contain letters, spaces, apostrophes and hyphens")
    private String lastName;

    @Pattern(regexp = "^[+]?[0-9\\s-()]+$", message = "Please provide a valid phone number")
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;

    private LocalDate dateOfBirth;
    private Gender gender;

    @NotBlank(message = "Role name is required (e.g., USER, ADMIN)")
    private String roleName;

    private Boolean isVerified;
    private Boolean isLegitProfile;
    private String accountStatus;
}
