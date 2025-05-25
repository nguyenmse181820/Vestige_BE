package se.vestige_be.dbinit;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import se.vestige_be.pojo.Role;
import se.vestige_be.repository.RoleRepository;

import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    public DataInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) {
        initRoles();
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
}
