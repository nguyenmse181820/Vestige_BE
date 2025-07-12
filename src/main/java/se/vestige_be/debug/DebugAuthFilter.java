package se.vestige_be.debug;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
@Slf4j
public class DebugAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        
        if (path.contains("/api/users/profile")) {
            log.info("=== DEBUG AUTH FILTER ===");
            log.info("Path: {}", path);
            log.info("Method: {}", request.getMethod());
            log.info("Authorization Header: {}", request.getHeader("Authorization"));
            log.info("Cookies: {}", request.getCookies() != null ? request.getCookies().length : 0);
            
            if (request.getCookies() != null) {
                for (var cookie : request.getCookies()) {
                    log.info("Cookie: {} = {}", cookie.getName(), cookie.getValue().substring(0, Math.min(20, cookie.getValue().length())) + "...");
                }
            }
            
            // Check security context BEFORE processing
            log.info("Security Context BEFORE: {}", SecurityContextHolder.getContext().getAuthentication());
            log.info("=== END DEBUG AUTH FILTER ===");
        }
        
        filterChain.doFilter(request, response);
        
        // Check security context AFTER processing
        if (path.contains("/api/users/profile")) {
            log.info("=== DEBUG AUTH FILTER AFTER ===");
            log.info("Security Context AFTER: {}", SecurityContextHolder.getContext().getAuthentication());
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                log.info("Principal: {}", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
                log.info("Authorities: {}", SecurityContextHolder.getContext().getAuthentication().getAuthorities());
            }
            log.info("=== END DEBUG AUTH FILTER AFTER ===");
        }
    }
}
