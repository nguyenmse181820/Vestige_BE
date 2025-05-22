package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.AuthRequest;
import se.vestige_be.dto.request.RegisterRequest;
import se.vestige_be.dto.response.AuthResponse;
import se.vestige_be.dto.request.RefreshTokenRequest;
import se.vestige_be.exception.TokenPossibleCompromiseException;
import se.vestige_be.exception.TokenRefreshException;
import se.vestige_be.pojo.RefreshToken;
import se.vestige_be.pojo.User;
import se.vestige_be.configuration.JWTTokenUtil;
import se.vestige_be.service.CustomUserDetailsService;
import se.vestige_be.service.RefreshTokenService;
import se.vestige_be.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "API for user authentication and token management")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JWTTokenUtil jwtTokenUtil;
    private final UserService userService;
    private final CustomUserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JWTTokenUtil jwtTokenUtil,
            UserService userService, CustomUserDetailsService userDetailsService,
            RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userService = userService;
        this.userDetailsService = userDetailsService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest loginRequest) {
        // Authenticate the user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userService.findByUsername(userDetails.getUsername());

        // Generate access token
        String accessToken = jwtTokenUtil.generateToken(userDetails);

        // Create new refresh token (with new family)
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

                            String accessToken = jwtTokenUtil.generateToken(userDetails);

                            // Rotate refresh token (revoke current one, create new one)
                            RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(refreshToken);

                            return ResponseEntity.ok(AuthResponse.builder()
                                    .accessToken(accessToken)
                                    .refreshToken(newRefreshToken.getToken())
                                    .userId(user.getUserId())
                                    .username(user.getUsername())
                                    .build());
                        } catch (TokenPossibleCompromiseException ex) {
                            refreshTokenService.handlePossibleTokenTheft(ex, refreshToken.getUser().getUsername());
                            logSecurityEvent(servletRequest, "Token theft detected",
                                    refreshToken.getUser().getUsername());

                            return ResponseEntity
                                    .status(HttpStatus.FORBIDDEN)
                                    .body(Map.of(
                                            "error", "Security alert",
                                            "message", "Your session has been terminated due to suspicious activity. Please login again."
                                    ));
                        }
                    })
                    .orElseThrow(() -> new TokenRefreshException(requestRefreshToken,
                            "Refresh token not found in database"));
        } catch (TokenRefreshException e) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid token", "message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (refreshToken == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Refresh token is required"));
        }

        try {
            refreshTokenService.revokeToken(refreshToken, "User logout");
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        } catch (Exception e) {
            // Even if token doesn't exist, we consider logout successful
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        }
    }

    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAllDevices(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            User user = userService.findByUsername(authentication.getName());
            refreshTokenService.revokeAllUserTokens(user, "User requested logout from all devices");
            return ResponseEntity.ok(Map.of("message", "Logged out from all devices successfully"));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Not authenticated"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        if (userService.existsByUsername(registerRequest.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("message", "Username is already taken!"));
        }

        // Check if email already exists
        if (userService.existsByEmail(registerRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("message", "Email is already in use!"));
        }

        // Create new user
        User user = userService.register(registerRequest);

        // Generate authentication tokens
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String accessToken = jwtTokenUtil.generateToken(userDetails);
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