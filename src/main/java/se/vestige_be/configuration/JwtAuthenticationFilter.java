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
            "/api/payos/webhook/health",
            "/api/ratings/stats",
            "/api/ratings/all",
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

        if (isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = null;
        String username = null;
        jwt = cookieUtil.getAccessTokenFromCookies(request.getCookies());

        if (jwt == null) {
            final String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
            }
        }

        if (jwt != null) {
            username = jwtTokenUtil.extractUsername(jwt);
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
            }
        }
        
        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        AntPathMatcher pathMatcher = new AntPathMatcher();
        String requestPath = request.getServletPath();
        
        // Special handling for membership endpoints - only plans and health endpoints are public
        if (requestPath.startsWith("/api/memberships/") && 
            "GET".equals(request.getMethod()) && 
            (requestPath.endsWith("/plans") || requestPath.endsWith("/health"))) {
            return true;
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
            // Public product endpoints - ONLY for GET requests
            if ("GET".equals(request.getMethod()) && (
                requestPath.matches("/api/products/?$") ||  // GET /api/products
                requestPath.matches("/api/products/\\d+$") ||  // GET /api/products/{id}
                requestPath.matches("/api/products/slug/[^/]+$") ||  // GET /api/products/slug/{slug}
                requestPath.matches("/api/products/slug-available/[^/]+$") ||  // GET /api/products/slug-available/{slug}
                requestPath.equals("/api/products/top-viewed"))) {  // GET /api/products/top-viewed
                return true;
            }
            // All other product endpoints (POST, PATCH, PUT, DELETE) are protected
            return false;
        }
        
        return publicEndpoints.stream()
                .anyMatch(p -> pathMatcher.match(p, requestPath));
    }
}
