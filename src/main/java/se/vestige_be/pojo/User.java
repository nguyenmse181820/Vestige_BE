package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true, length = 50, columnDefinition = "varchar(50)")
    private String username;

    @Column(nullable = false, unique = true, length = 100, columnDefinition = "varchar(100)")
    private String email;

    @Column(nullable = false, length = 255, columnDefinition = "varchar(255)")
    private String passwordHash;

    @Column(length = 50, columnDefinition = "varchar(50)")
    private String firstName;

    @Column(length = 50, columnDefinition = "varchar(50)")
    private String lastName;

    @Column(length = 255, columnDefinition = "varchar(255)")
    private String profilePictureUrl;

    @Column(columnDefinition = "text")
    private String bio;

    @CreationTimestamp
    private LocalDateTime joinedDate;

    private BigDecimal sellerRating;
    private Integer sellerReviewsCount;
    private Integer successfulTransactions;

    private Boolean isLegitProfile;
    private Boolean isVerified;

    private BigDecimal trustScore;
    private LocalDateTime lastLoginAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserAddress> addresses;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserMembership> memberships;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserRole> roles;

    @OneToMany(mappedBy = "seller", cascade = CascadeType.ALL)
    private List<Product> products;

    @OneToMany(mappedBy = "buyer", cascade = CascadeType.ALL)
    private List<Offer> offers;

    @OneToMany(mappedBy = "buyer", cascade = CascadeType.ALL)
    private List<Order> orders;

    @OneToMany(mappedBy = "seller", cascade = CascadeType.ALL)
    private List<OrderItem> soldItems;

    @OneToMany(mappedBy = "seller", cascade = CascadeType.ALL)
    private List<Transaction> sellerTransactions;

    @OneToMany(mappedBy = "buyer", cascade = CascadeType.ALL)
    private List<Transaction> buyerTransactions;

    @OneToMany(mappedBy = "reviewer", cascade = CascadeType.ALL)
    private List<Review> givenReviews;

    @OneToMany(mappedBy = "reviewedUser", cascade = CascadeType.ALL)
    private List<Review> receivedReviews;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Notification> notifications;

    @OneToMany(mappedBy = "follower", cascade = CascadeType.ALL)
    private List<UserFollow> following;

    @OneToMany(mappedBy = "following", cascade = CascadeType.ALL)
    private List<UserFollow> followers;
}
}
