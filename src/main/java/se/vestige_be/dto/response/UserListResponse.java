package se.vestige_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import se.vestige_be.pojo.enums.Gender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListResponse {
    private Long userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String profilePictureUrl;
    private LocalDateTime joinedDate;
    private BigDecimal sellerRating;
    private Integer sellerReviewsCount;
    private Integer successfulTransactions;
    private Boolean isLegitProfile;
    private Boolean isVerified;
    private String accountStatus;
    private Integer trustScore;
    private LocalDateTime lastLoginAt;
    private String roleName;

    // Basic stats
    private Integer totalProductsListed;
    private Integer activeProductsCount;
}