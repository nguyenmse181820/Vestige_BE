package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import se.vestige_be.util.CookieUtil;

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
    private final CookieUtil cookieUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest loginRequest, HttpServletResponse response) {
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

        // Generate both tokens
        String accessToken = jwtTokenUtil.generateToken(userDetails, claims);
        String refreshToken = jwtTokenUtil.generateRefreshToken(userDetails);

        // Store tokens in httpOnly cookies
        cookieUtil.createAccessTokenCookie(response, accessToken);
        cookieUtil.createRefreshTokenCookie(response, refreshToken);

        // Also store refresh token in database for additional security
        RefreshToken dbRefreshToken = refreshTokenService.createNewRefreshTokenFamily(userDetails.getUsername());

        // Return user info without tokens (tokens are now in cookies)
        return ResponseEntity.ok(AuthResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(userRole)
                .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        // Get refresh token from cookie instead of request body
        String requestRefreshToken = cookieUtil.getRefreshTokenFromCookies(request.getCookies());

        if (requestRefreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ObjectResponse.builder()
                            .status("error")
                            .message("Refresh token not found")
                            .build());
        }

        try {
            // Validate the JWT refresh token
            if (!jwtTokenUtil.isRefreshToken(requestRefreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ObjectResponse.builder()
                                .status("error")
                                .message("Invalid token type")
                                .build());
            }

            String username = jwtTokenUtil.extractUsername(requestRefreshToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (!jwtTokenUtil.validateToken(requestRefreshToken, userDetails)) {
                cookieUtil.clearAllTokenCookies(response);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ObjectResponse.builder()
                                .status("error")
                                .message("Invalid refresh token")
                                .build());
            }

            // Generate new tokens
            User user = userService.findByUsername(username);
            Map<String, Object> claims = new HashMap<>();
            claims.put("email", user.getEmail());
            String userRole = (user.getRole() != null && user.getRole().getName() != null)
                    ? user.getRole().getName().toUpperCase()
                    : "USER";
            claims.put("role", userRole);

            String newAccessToken = jwtTokenUtil.generateToken(userDetails, claims);
            String newRefreshToken = jwtTokenUtil.generateRefreshToken(userDetails);

            // Update cookies with new tokens
            cookieUtil.createAccessTokenCookie(response, newAccessToken);
            cookieUtil.createRefreshTokenCookie(response, newRefreshToken);

            return ResponseEntity.ok(ObjectResponse.builder()
                    .status("success")
                    .message("Tokens refreshed successfully")
                    .build());

        } catch (Exception e) {
            cookieUtil.clearAllTokenCookies(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ObjectResponse.builder()
                            .status("error")
                            .message("Token refresh failed: " + e.getMessage())
                            .build());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        // Clear cookies
        cookieUtil.clearAllTokenCookies(response);

        // Optionally, also revoke refresh token from database if you want to keep that functionality
        String refreshToken = cookieUtil.getRefreshTokenFromCookies(request.getCookies());
        if (refreshToken != null) {
            try {
                // You can still revoke database refresh tokens if needed
                // refreshTokenService.revokeToken(refreshToken, "User logout");
            } catch (Exception e) {
                // Log but don't fail the logout
            }
        }

        return ResponseEntity.ok(ObjectResponse.builder()
                .status("success")
                .message("Logged out successfully")
                .build());
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
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest, HttpServletResponse response) {
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

        // Generate both tokens
        String accessToken = jwtTokenUtil.generateToken(userDetails, claims);
        String refreshToken = jwtTokenUtil.generateRefreshToken(userDetails);

        // Store tokens in httpOnly cookies
        cookieUtil.createAccessTokenCookie(response, accessToken);
        cookieUtil.createRefreshTokenCookie(response, refreshToken);

        // Also store refresh token in database for additional security
        RefreshToken dbRefreshToken = refreshTokenService.createNewRefreshTokenFamily(userDetails.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                AuthResponse.builder()
                        .userId(user.getUserId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(userRole)
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