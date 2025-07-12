package se.vestige_be.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import se.vestige_be.util.CookieUtil;
import se.vestige_be.configuration.JWTTokenUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JWTTokenUtil jwtTokenUtil;
    private final UserDetailsService userDetailsService;
    private final CookieUtil cookieUtil;

    private final List<String> publicEndpoints = Arrays.asList(
            "/api/auth/**",
            "/api/stripe/webhook",
            "/api/payos/webhook",
            "/api/v1/payos/payment-callback",
            "/ws/**",
            "/chat/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/**"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String requestPath = request.getServletPath();
        boolean isProfileRequest = requestPath.contains("/api/users/profile");
        
        if (isProfileRequest) {
            System.out.println("=== JWT FILTER DEBUG ===");
            System.out.println("Request Path: " + requestPath);
        }

        if (isPublicEndpoint(request)) {
            if (isProfileRequest) {
                System.out.println("This is a PUBLIC endpoint - skipping authentication");
                System.out.println("=== END JWT FILTER DEBUG ===");
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (isProfileRequest) {
            System.out.println("This is a PROTECTED endpoint - processing authentication");
        }

        String jwt = null;
        String username = null;
        jwt = cookieUtil.getAccessTokenFromCookies(request.getCookies());

        if (isProfileRequest) {
            System.out.println("JWT from cookies: " + (jwt != null ? jwt.substring(0, Math.min(20, jwt.length())) + "..." : "null"));
        }

        if (jwt == null) {
            final String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
            }
        }

        if (jwt != null) {
            username = jwtTokenUtil.extractUsername(jwt);
            if (isProfileRequest) {
                System.out.println("Extracted username: " + username);
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
            if (jwtTokenUtil.validateToken(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                
                if (isProfileRequest) {
                    System.out.println("Authentication successful! Authorities: " + userDetails.getAuthorities());
                }
            } else {
                if (isProfileRequest) {
                    System.out.println("Token validation FAILED!");
                }
            }
        } else {
            if (isProfileRequest) {
                System.out.println("No username extracted or authentication already exists");
                System.out.println("Username: " + username);
                System.out.println("Existing auth: " + SecurityContextHolder.getContext().getAuthentication());
            }
        }
        
        if (isProfileRequest) {
            System.out.println("=== END JWT FILTER DEBUG ===");
        }
        
        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        AntPathMatcher pathMatcher = new AntPathMatcher();
        String requestPath = request.getServletPath();
        
        boolean isProfileRequest = requestPath.contains("/api/users/profile");
        boolean isMyProductsRequest = requestPath.contains("/api/products/my-products");
        
        if (isProfileRequest || isMyProductsRequest) {
            System.out.println("=== PUBLIC ENDPOINT CHECK ===");
            System.out.println("Request Path: " + requestPath);
            
            for (String pattern : publicEndpoints) {
                boolean matches = pathMatcher.match(pattern, requestPath);
                System.out.println("Pattern: " + pattern + " -> Matches: " + matches);
                if (matches) {
                    System.out.println("MATCHED PUBLIC PATTERN: " + pattern);
                    System.out.println("=== END PUBLIC ENDPOINT CHECK ===");
                    return true;
                }
            }
            System.out.println("NO PUBLIC PATTERNS MATCHED");
            System.out.println("=== END PUBLIC ENDPOINT CHECK ===");
        }
        
        // Special handling for categories and brands - only GET operations are public
        if (requestPath.startsWith("/api/categories/") || requestPath.startsWith("/api/brands/")) {
            return "GET".equals(request.getMethod());
        }
        
        // Special handling for /api/users/{id} - should only match numeric IDs
        if (requestPath.matches("/api/users/\\d+")) {
            return true;
        }
        
        // Special handling for product endpoints - only specific ones should be public
        if (requestPath.startsWith("/api/products/")) {
            // Public product endpoints
            if (requestPath.matches("/api/products/?$") ||  // GET /api/products
                requestPath.matches("/api/products/\\d+$") ||  // GET /api/products/{id}
                requestPath.matches("/api/products/slug/[^/]+$") ||  // GET /api/products/slug/{slug}
                requestPath.matches("/api/products/slug-available/[^/]+$") ||  // GET /api/products/slug-available/{slug}
                requestPath.equals("/api/products/top-viewed")) {  // GET /api/products/top-viewed
                return true;
            }
            // All other product endpoints are protected
            return false;
        }
        
        return publicEndpoints.stream()
                .anyMatch(p -> pathMatcher.match(p, requestPath));
    }
}
