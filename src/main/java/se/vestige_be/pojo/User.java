package se.vestige_be.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import se.vestige_be.pojo.enums.Gender;
import se.vestige_be.pojo.enums.TrustTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    @JsonIgnore
    private String passwordHash;

    public void setPassword(String password) {
        this.passwordHash = password;
    }

    @Column(length = 50)
    private String firstName;

    @Column(length = 50)
    private String lastName;

    @Column(length = 20)
    private String phoneNumber;

    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(length = 255)
    private String profilePictureUrl;

    @Column(columnDefinition = "text")
    private String bio;

    @CreationTimestamp
    private LocalDateTime joinedDate;

    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal sellerRating = BigDecimal.ZERO;

    @Builder.Default
    private Integer sellerReviewsCount = 0;

    @Builder.Default
    private Integer successfulTransactions = 0;

    @Builder.Default
    private Boolean isVerified = false;

    @Column(length = 20)
    @Builder.Default
    private String accountStatus = "active";

    /**
     * Sets whether the account is active or inactive
     *
     * @param isActive true to set status to "active", false to set status to "inactive"
     */
    public void setIsActive(Boolean isActive) {
        this.accountStatus = isActive ? "active" : "inactive";
    }

    /**
     * Checks if the account is active
     *
     * @return true if account status is "active", false otherwise
     */
    public Boolean getIsActive() {
        return "active".equals(this.accountStatus);
    }

    @Column(name = "trust_score")
    @Builder.Default
    private Integer trustScore = 100; // Default score for new users

    @Enumerated(EnumType.STRING)
    @Column(name = "trust_tier")
    @Builder.Default
    private TrustTier trustTier = TrustTier.NEW_SELLER; // Default tier

    private LocalDateTime lastLoginAt;

    @OneToMany(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<UserAddress> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<UserMembership> memberships = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    @ToString.Exclude
    private Role role;

    @OneToMany(mappedBy = "seller", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Product> products = new ArrayList<>();

    @OneToMany(mappedBy = "buyer", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Offer> offers = new ArrayList<>();

    @OneToMany(mappedBy = "buyer", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "seller", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<OrderItem> soldItems = new ArrayList<>();

    @OneToMany(mappedBy = "seller", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Transaction> sellerTransactions = new ArrayList<>();

    @OneToMany(mappedBy = "buyer", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Transaction> buyerTransactions = new ArrayList<>();

    @OneToMany(mappedBy = "reviewer", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Review> givenReviews = new ArrayList<>();

    @OneToMany(mappedBy = "reviewedUser", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Review> receivedReviews = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Notification> notifications = new ArrayList<>();

    @OneToMany(mappedBy = "follower", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<UserFollow> following = new ArrayList<>();

    @OneToMany(mappedBy = "followedUser", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<UserFollow> followers = new ArrayList<>();

    private String stripeAccountId;
}