package se.vestige_be.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.request.RegisterRequest;
import se.vestige_be.pojo.Role;
import se.vestige_be.pojo.User;
import se.vestige_be.repository.RoleRepository;
import se.vestige_be.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
    }

    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
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

        user = userRepository.save(user);

        return user;
    }

    @Transactional
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public User updateLastLogin(String username) {
        User user = findByUsername(username);
        user.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    @Transactional
    public User changePassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    @Transactional
    public User updateProfile(Long userId, String firstName, String lastName, String bio, String profilePictureUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setBio(bio);
        user.setProfilePictureUrl(profilePictureUrl);

        return userRepository.save(user);
    }

    @Transactional
    public User updateLegitProfileStatus(Long userId, boolean isLegitProfile) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setIsLegitProfile(isLegitProfile);
        return userRepository.save(user);
    }

    @Transactional
    public User updateVerificationStatus(Long userId, boolean isVerified) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setIsVerified(isVerified);
        return userRepository.save(user);
    }

    @Transactional
    public User updateAccountStatus(Long userId, String accountStatus) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setAccountStatus(accountStatus);
        return userRepository.save(user);
    }

    @Transactional
    public User incrementSuccessfulTransactions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setSuccessfulTransactions(user.getSuccessfulTransactions() + 1);
        return userRepository.save(user);
    }

    @Transactional
    public User updateSellerRating(Long userId, BigDecimal newRating, int reviewsCount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setSellerRating(newRating);
        user.setSellerReviewsCount(reviewsCount);
        return userRepository.save(user);
    }

    @Transactional
    public User updateTrustScore(Long userId, BigDecimal trustScore) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setTrustScore(trustScore);
        return userRepository.save(user);
    }
}