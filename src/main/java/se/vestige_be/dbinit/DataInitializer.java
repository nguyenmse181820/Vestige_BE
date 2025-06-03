package se.vestige_be.dbinit;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import se.vestige_be.pojo.Role;
import se.vestige_be.pojo.User;
import se.vestige_be.repository.RoleRepository;
import se.vestige_be.repository.UserRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RoleRepository roleRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        initRoles();
        initUsers();
    }

    private void initRoles() {
        try {
            // Check if any roles exist
            long roleCount = roleRepository.count();

            if (roleCount == 0) {
                // Create only the needed roles
                List<Role> roles = new ArrayList<>();

                roles.add(Role.builder()
                        .name("USER")
                        .build());

                roles.add(Role.builder()
                        .name("ADMIN")
                        .build());

                roleRepository.saveAll(roles);

                System.out.println("Default roles have been created");
            }
        } catch (Exception e) {
            System.err.println("Error initializing roles: " + e.getMessage());
        }
    }

    private void initUsers() {
        try {
            // Check if any users exist
            long userCount = userRepository.count();

            if (userCount == 0) {
                // Get roles
                Role userRole = roleRepository.findByName("USER")
                        .orElseThrow(() -> new RuntimeException("USER role not found"));
                Role adminRole = roleRepository.findByName("ADMIN")
                        .orElseThrow(() -> new RuntimeException("ADMIN role not found"));

                // Common password for all users
                String commonPassword = "Nguyenm$E181820";
                String encodedPassword = passwordEncoder.encode(commonPassword);

                List<User> users = new ArrayList<>();

                // Admin User
                users.add(User.builder()
                        .username("admin")
                        .email("admin@vestige.com")
                        .passwordHash(encodedPassword)
                        .firstName("Admin")
                        .lastName("User")
                        .bio("Platform administrator")
                        .role(adminRole)
                        .isVerified(true)
                        .accountStatus("active")
                        .trustScore(new BigDecimal("5.0"))
                        .build());

                // Regular Users
                users.add(User.builder()
                        .username("johndoe")
                        .email("john.doe@example.com")
                        .passwordHash(encodedPassword)
                        .firstName("John")
                        .lastName("Doe")
                        .bio("Fashion enthusiast and collector")
                        .role(userRole)
                        .isVerified(true)
                        .accountStatus("active")
                        .sellerRating(new BigDecimal("4.5"))
                        .sellerReviewsCount(25)
                        .successfulTransactions(20)
                        .trustScore(new BigDecimal("4.2"))
                        .build());

                users.add(User.builder()
                        .username("jansmith")
                        .email("jane.smith@example.com")
                        .passwordHash(encodedPassword)
                        .firstName("Jane")
                        .lastName("Smith")
                        .bio("Luxury handbag specialist")
                        .role(userRole)
                        .isVerified(true)
                        .isLegitProfile(true)
                        .accountStatus("active")
                        .sellerRating(new BigDecimal("4.8"))
                        .sellerReviewsCount(45)
                        .successfulTransactions(38)
                        .trustScore(new BigDecimal("4.7"))
                        .build());

                users.add(User.builder()
                        .username("mikewilson")
                        .email("mike.wilson@example.com")
                        .passwordHash(encodedPassword)
                        .firstName("Mike")
                        .lastName("Wilson")
                        .bio("Watch collector and trader")
                        .role(userRole)
                        .isVerified(true)
                        .accountStatus("active")
                        .sellerRating(new BigDecimal("4.3"))
                        .sellerReviewsCount(12)
                        .successfulTransactions(10)
                        .trustScore(new BigDecimal("4.0"))
                        .build());

                userRepository.saveAll(users);

                System.out.println("Default users have been created:");
                System.out.println("- admin / admin@vestige.com (ADMIN)");
                System.out.println("- johndoe / john.doe@example.com (USER)");
                System.out.println("- jansmith / jane.smith@example.com (USER - Legit Profile)");
                System.out.println("- mikewilson / mike.wilson@example.com (USER)");
                System.out.println("All users have password: Nguyenm$E181820");
            }
        } catch (Exception e) {
            System.err.println("Error initializing users: " + e.getMessage());
        }
    }
}