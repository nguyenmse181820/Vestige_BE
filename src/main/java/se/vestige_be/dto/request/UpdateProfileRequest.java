package se.vestige_be.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import se.vestige_be.pojo.enums.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
    @Pattern(regexp = "^[a-zA-ZÀ-ÿ\\s'-]+$", message = "First name can only contain letters, spaces, apostrophes and hyphens")
    private String firstName;

    @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
    @Pattern(regexp = "^[a-zA-ZÀ-ÿ\\s'-]+$", message = "Last name can only contain letters, spaces, apostrophes and hyphens")
    private String lastName;

    @Pattern(regexp = "^[+]?[0-9\\s-()]+$", message = "Please provide a valid phone number")
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;

    private LocalDate dateOfBirth;

    private Gender gender;

    @Size(max = 1000, message = "Bio must not exceed 1000 characters")
    private String bio;

    @Size(max = 255, message = "Profile picture URL must not exceed 255 characters")
    private String profilePictureUrl;

    public boolean hasFirstName() {
        return firstName != null;
    }

    public boolean hasLastName() {
        return lastName != null;
    }

    public boolean hasPhoneNumber() {
        return phoneNumber != null;
    }

    public boolean hasDateOfBirth() {
        return dateOfBirth != null;
    }

    public boolean hasGender() {
        return gender != null;
    }

    public boolean hasBio() {
        return bio != null;
    }

    public boolean hasProfilePictureUrl() {
        return profilePictureUrl != null;
    }
}