package se.vestige_be.service;

import com.stripe.model.Account;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.request.AdminCreateUserRequest;
import se.vestige_be.dto.request.RegisterRequest;
import se.vestige_be.dto.request.UpdateProfileRequest;
import se.vestige_be.dto.response.PagedResponse;
import se.vestige_be.dto.response.UserListResponse;
import se.vestige_be.dto.response.UserProfileResponse;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.exception.ResourceNotFoundException;
import se.vestige_be.pojo.Role;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.enums.ProductStatus;
import se.vestige_be.repository.ProductRepository;
import se.vestige_be.repository.RoleRepository;
import se.vestige_be.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
    }

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with userId: " + userId));
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public PagedResponse<UserListResponse> getAllUsers(Pageable pageable, String search, Boolean isLegitProfile, String accountStatus) {
        Specification<User> spec = buildUserSpecification(search, isLegitProfile, accountStatus);
        Page<User> users = userRepository.findAll(spec, pageable);
        Page<UserListResponse> userResponses = users.map(this::convertToListResponse);
        return PagedResponse.of(userResponses);
    }

    public UserProfileResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return convertToProfileResponse(user);
    }

    public UserProfileResponse getUserProfile(String username) {
        User user = findByUsername(username);
        return convertToProfileResponse(user);
    }

    private Specification<User> buildUserSpecification(String search, Boolean isLegitProfile, String accountStatus) {
        return Specification.where(hasSearch(search))
                .and(hasLegitProfile(isLegitProfile))
                .and(hasAccountStatus(accountStatus));
    }

    private Specification<User> hasSearch(String search) {
        return (root, query, criteriaBuilder) -> {
            if (search == null || search.trim().isEmpty()) {
                return null;
            }
            String likePattern = "%" + search.toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), likePattern)
            );
        };
    }

    private Specification<User> hasLegitProfile(Boolean isLegitProfile) {
        return (root, query, criteriaBuilder) -> {
            if (isLegitProfile == null) {
                return null;
            }
            return criteriaBuilder.equal(root.get("isLegitProfile"), isLegitProfile);
        };
    }

    private Specification<User> hasAccountStatus(String accountStatus) {
        return (root, query, criteriaBuilder) -> {
            if (accountStatus == null || accountStatus.trim().isEmpty()) {
                return null;
            }
            return criteriaBuilder.equal(root.get("accountStatus"), accountStatus);
        };
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        Role userRole = roleRepository.findByName("USER").orElseThrow(() -> new RuntimeException("Role not found"));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .sellerRating(BigDecimal.ZERO)
                .sellerReviewsCount(0)
                .successfulTransactions(0)
                .isLegitProfile(false)
                .isVerified(false)
                .trustScore(BigDecimal.ZERO)
                .accountStatus("active")
                .joinedDate(LocalDateTime.now())
                .addresses(new ArrayList<>())
                .memberships(new ArrayList<>())
                .role(userRole)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public void handleStripeAccountUpdate(Account account) {
        userRepository.findByStripeAccountId(account.getId()).ifPresent(user -> {
            log.info("Updating user {} based on Stripe account update from webhook.", user.getUsername());

            boolean isVerified = Boolean.TRUE.equals(account.getChargesEnabled()) && Boolean.TRUE.equals(account.getPayoutsEnabled());
            user.setIsVerified(isVerified);

            String disabledReason = null;
            if (account.getRequirements() != null) {
                disabledReason = account.getRequirements().getDisabledReason();
            }

            if (disabledReason != null) {
                user.setAccountStatus("restricted");
                log.warn("Stripe account for user {} has been restricted. Reason: {}", user.getUsername(), disabledReason);
            } else if (isVerified) {
                user.setAccountStatus("active");
            } else {
                // Nếu không được xác minh và cũng không có lý do vô hiệu hóa rõ ràng
                // có thể tài khoản đang ở trạng thái 'pending' hoặc 'incomplete'
                user.setAccountStatus("pending_verification");
            }
            userRepository.save(user);
            log.info("User {} verification status set to: {}. Account status set to: {}",
                    user.getUsername(), isVerified, user.getAccountStatus());
        });
    }

    @Transactional
    public User processLogin(String username) {
        User user = findByUsername(username);
        user.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        User user = findByUsername(username);

        if (request.hasFirstName()) {
            user.setFirstName(request.getFirstName());
        }

        if (request.hasLastName()) {
            user.setLastName(request.getLastName());
        }

        if (request.hasPhoneNumber()) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.hasDateOfBirth()) {
            user.setDateOfBirth(request.getDateOfBirth());
        }

        if (request.hasGender()) {
            user.setGender(request.getGender());
        }

        if (request.hasBio()) {
            user.setBio(request.getBio());
        }

        if (request.hasProfilePictureUrl()) {
            user.setProfilePictureUrl(request.getProfilePictureUrl());
        }

        User savedUser = userRepository.save(user);
        return convertToProfileResponse(savedUser);
    }

    private UserListResponse convertToListResponse(User user) {
        Long totalProductsListed = productRepository.countBySellerUserId(user.getUserId());
        Long activeProductsCount = productRepository.countBySellerUserIdAndStatus(user.getUserId(), ProductStatus.ACTIVE);

        return UserListResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .dateOfBirth(user.getDateOfBirth())
                .gender(user.getGender())
                .profilePictureUrl(user.getProfilePictureUrl())
                .joinedDate(user.getJoinedDate())
                .sellerRating(user.getSellerRating())
                .sellerReviewsCount(user.getSellerReviewsCount())
                .successfulTransactions(user.getSuccessfulTransactions())
                .isLegitProfile(user.getIsLegitProfile())
                .isVerified(user.getIsVerified())
                .accountStatus(user.getAccountStatus())
                .trustScore(user.getTrustScore())
                .lastLoginAt(user.getLastLoginAt())
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .totalProductsListed(totalProductsListed.intValue())
                .activeProductsCount(activeProductsCount.intValue())
                .build();
    }

    @Transactional
    public UserProfileResponse createUserByAdmin(AdminCreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessLogicException("Username '" + request.getUsername() + "' already exists.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessLogicException("Email '" + request.getEmail() + "' already exists.");
        }

        Role roleToAssign = roleRepository.findByName(request.getRoleName().toUpperCase())
                .orElseThrow(() -> {
                    return new BusinessLogicException("Role '" + request.getRoleName() + "' not found. Ensure roles like USER, ADMIN exist.");
                });

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .role(roleToAssign)
                .isVerified(request.getIsVerified() != null ? request.getIsVerified() : false)
                .isLegitProfile(request.getIsLegitProfile() != null ? request.getIsLegitProfile() : false)
                .accountStatus(request.getAccountStatus() != null ? request.getAccountStatus().toLowerCase() : "active")
                .sellerRating(BigDecimal.ZERO)
                .sellerReviewsCount(0)
                .successfulTransactions(0)
                .trustScore(BigDecimal.ZERO)
                .joinedDate(LocalDateTime.now())
                .addresses(new ArrayList<>())
                .memberships(new ArrayList<>())
                .build();

        User savedUser = userRepository.save(user);
        return convertToProfileResponse(savedUser);
    }

    private UserProfileResponse convertToProfileResponse(User user) {
        Long totalProductsListed = productRepository.countBySellerUserId(user.getUserId());
        Long activeProductsCount = productRepository.countBySellerUserIdAndStatus(user.getUserId(), ProductStatus.ACTIVE);
        Long soldProductsCount = productRepository.countBySellerUserIdAndStatus(user.getUserId(), ProductStatus.SOLD);

        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .dateOfBirth(user.getDateOfBirth())
                .gender(user.getGender())
                .profilePictureUrl(user.getProfilePictureUrl())
                .bio(user.getBio())
                .joinedDate(user.getJoinedDate())
                .sellerRating(user.getSellerRating())
                .sellerReviewsCount(user.getSellerReviewsCount())
                .successfulTransactions(user.getSuccessfulTransactions())
                .isLegitProfile(user.getIsLegitProfile())
                .isVerified(user.getIsVerified())
                .accountStatus(user.getAccountStatus())
                .trustScore(user.getTrustScore())
                .lastLoginAt(user.getLastLoginAt())
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .totalProductsListed(totalProductsListed.intValue())
                .activeProductsCount(activeProductsCount.intValue())
                .soldProductsCount(soldProductsCount.intValue())
                .build();
    }
}