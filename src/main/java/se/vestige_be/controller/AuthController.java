package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.AuthRequest;
import se.vestige_be.dto.request.RegisterRequest;
import se.vestige_be.dto.response.AuthResponse;
import se.vestige_be.dto.request.RefreshTokenRequest;
import se.vestige_be.dto.response.ObjectResponse;
import se.vestige_be.exception.TokenPossibleCompromiseException;
import se.vestige_be.exception.TokenRefreshException;
import se.vestige_be.pojo.RefreshToken;
import se.vestige_be.pojo.User;
import se.vestige_be.configuration.JWTTokenUtil;
import se.vestige_be.service.CustomUserDetailsService;
import se.vestige_be.service.RefreshTokenService;
import se.vestige_be.service.UserService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "API for user authentication and token management")
@AllArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JWTTokenUtil jwtTokenUtil;
    private final UserService userService;
    private final CustomUserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userService.processLogin(userDetails.getUsername());

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        String userRole = (user.getRole() != null && user.getRole().getName() != null)
                ? user.getRole().getName().toUpperCase()
                : "USER";
        claims.put("role", userRole);

        String accessToken = jwtTokenUtil.generateToken(userDetails, claims);
        RefreshToken refreshToken = refreshTokenService.createNewRefreshTokenFamily(userDetails.getUsername());

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .userId(user.getUserId())
                .username(user.getUsername())
                .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
        String requestRefreshToken = request.getRefreshToken();

        try {
            return refreshTokenService.findByToken(requestRefreshToken)
                    .map(refreshToken -> {
                        try {
                            refreshTokenService.verifyExpiration(refreshToken);

                            User user = refreshToken.getUser();
                            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());

                            Map<String, Object> claims = new HashMap<>();
                            claims.put("email", user.getEmail());
                            String userRole = (user.getRole() != null && user.getRole().getName() != null)
                                    ? user.getRole().getName().toUpperCase()
                                    : "USER";
                            claims.put("role", userRole);

                            String accessToken = jwtTokenUtil.generateToken(userDetails, claims);
                            RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(refreshToken);

                            return ResponseEntity.ok(AuthResponse.builder()
                                    .accessToken(accessToken)
                                    .refreshToken(newRefreshToken.getToken())
                                    .userId(user.getUserId())
                                    .username(user.getUsername())
                                    .build());
                        } catch (TokenPossibleCompromiseException ex) {
                            refreshTokenService.handlePossibleTokenTheft(ex, refreshToken.getUser().getUsername());
                            logSecurityEvent(servletRequest, "Token theft detected", refreshToken.getUser().getUsername());

                            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body(ObjectResponse.builder()
                                            .status("error")
                                            .message("Your session has been terminated due to suspicious activity. Please login again.")
                                            .build());
                        }
                    })
                    .orElseThrow(() -> new TokenRefreshException(requestRefreshToken, "Refresh token not found in database"));
        } catch (TokenRefreshException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ObjectResponse.builder()
                            .status("error")
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (refreshToken == null) {
            return ResponseEntity.badRequest()
                    .body(ObjectResponse.builder()
                            .status("error")
                            .message("Refresh token is required")
                            .build());
        }

        try {
            refreshTokenService.revokeToken(refreshToken, "User logout");
            return ResponseEntity.ok(ObjectResponse.builder()
                    .status("success")
                    .message("Logged out successfully")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.ok(ObjectResponse.builder()
                    .status("success")
                    .message("Logged out successfully")
                    .build());
        }
    }

    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAllDevices(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            User user = userService.findByUsername(username);

            if (user != null) {
                refreshTokenService.revokeAllUserTokens(user, "User requested logout from all devices");
                return ResponseEntity.ok(ObjectResponse.builder()
                        .status("success")
                        .message("Logged out from all devices successfully")
                        .build());
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ObjectResponse.builder()
                                .status("error")
                                .message("User not found")
                                .build());
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ObjectResponse.builder()
                        .status("error")
                        .message("Not authenticated")
                        .build());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userService.existsByUsername(registerRequest.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(ObjectResponse.builder()
                            .status("error")
                            .message("Username is already taken!")
                            .build());
        }

        if (userService.existsByEmail(registerRequest.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(ObjectResponse.builder()
                            .status("error")
                            .message("Email is already in use!")
                            .build());
        }

        User user = userService.register(registerRequest);
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        String userRole = (user.getRole() != null && user.getRole().getName() != null)
                ? user.getRole().getName().toUpperCase()
                : "USER";
        claims.put("role", userRole);

        String accessToken = jwtTokenUtil.generateToken(userDetails, claims);
        RefreshToken refreshToken = refreshTokenService.createNewRefreshTokenFamily(userDetails.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken.getToken())
                        .userId(user.getUserId())
                        .username(user.getUsername())
                        .build()
        );
    }

    private void logSecurityEvent(HttpServletRequest request, String event, String username) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null) {
            ipAddress = request.getRemoteAddr();
        }

        String userAgent = request.getHeader("User-Agent");

        System.out.println("SECURITY EVENT: " + event);
        System.out.println("User: " + username);
        System.out.println("IP: " + ipAddress);
        System.out.println("User-Agent: " + userAgent);
        System.out.println("Timestamp: " + java.time.Instant.now());
    }
}