package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.UpdateProfileRequest;
import se.vestige_be.dto.response.ObjectResponse;
import se.vestige_be.service.UserService;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User", description = "API for user management")
@AllArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> findAll() {
        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status(HttpStatus.OK.toString())
                        .message("Successfully retrieved all users.")
                        .data(userService.findAllUsers())
                        .build()
        );
    }

    @GetMapping("/profiles/{id}")
    public ResponseEntity<?> getUserProfile(@PathVariable Long id) {
        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status(HttpStatus.OK.toString())
                        .message("Successfully retrieved user profile")
                        .data(userService.findById(id))
                .build()
        );
    }

    @PatchMapping("/profiles/{id}")
    public ResponseEntity<?> updateUserProfile(
            @PathVariable Long id,
            @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status(HttpStatus.OK.toString())
                        .message("Successfully updated user profile")
                        .data(userService.updateProfile(id, request))
                        .build()
        );
    }
}
