package se.vestige_be.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    @Value("${jwt.expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpiration;

    // Debug methods - REMOVE IN PRODUCTION
    @Getter
    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Getter
    @Value("${app.cookie.same-site}")
    private String cookieSameSite;

    @Getter
    @Value("${app.cookie.domain:}")
    private String cookieDomain;

    private static final String ACCESS_TOKEN_COOKIE = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    /**
     * Create httpOnly cookie for access token
     */
    public void createAccessTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(ACCESS_TOKEN_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge((int) (accessTokenExpiration / 1000)); // Convert to seconds
        
        if (!cookieDomain.isEmpty()) {
            cookie.setDomain(cookieDomain);
        }
        
        // Set SameSite attribute via response header since Cookie class doesn't support it directly
        setSameSiteAttribute(response, cookie, cookieSameSite);
        response.addCookie(cookie);
    }

    /**
     * Create httpOnly cookie for refresh token
     */
    public void createRefreshTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/"); // Make available site-wide like access token
        cookie.setMaxAge((int) (refreshTokenExpiration / 1000)); // Convert to seconds
        
        if (!cookieDomain.isEmpty()) {
            cookie.setDomain(cookieDomain);
        }
        
        // Set SameSite attribute via response header
        setSameSiteAttribute(response, cookie, cookieSameSite);
        response.addCookie(cookie);
    }

    /**
     * Extract access token from cookie
     */
    public String getAccessTokenFromCookies(Cookie[] cookies) {
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Extract refresh token from cookie
     */
    public String getRefreshTokenFromCookies(Cookie[] cookies) {
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Clear access token cookie
     */
    public void clearAccessTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(ACCESS_TOKEN_COOKIE, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        
        if (!cookieDomain.isEmpty()) {
            cookie.setDomain(cookieDomain);
        }
        
        response.addCookie(cookie);
    }

    /**
     * Clear refresh token cookie
     */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/"); // Match the path used when creating
        cookie.setMaxAge(0);
        
        if (!cookieDomain.isEmpty()) {
            cookie.setDomain(cookieDomain);
        }
        
        response.addCookie(cookie);
    }

    /**
     * Clear both tokens
     */
    public void clearAllTokenCookies(HttpServletResponse response) {
        clearAccessTokenCookie(response);
        clearRefreshTokenCookie(response);
    }

    /**
     * Helper method to set SameSite attribute via response header
     * since Jakarta Servlet Cookie class doesn't support SameSite directly
     */
    private void setSameSiteAttribute(HttpServletResponse response, Cookie cookie, String sameSite) {
        if (sameSite != null && !sameSite.isEmpty()) {
            String cookieHeader = String.format("%s=%s; Path=%s; HttpOnly", 
                    cookie.getName(), 
                    cookie.getValue() != null ? cookie.getValue() : "",
                    cookie.getPath());
            
            if (cookie.getSecure()) {
                cookieHeader += "; Secure";
            }
            
            if (cookie.getDomain() != null && !cookie.getDomain().isEmpty()) {
                cookieHeader += "; Domain=" + cookie.getDomain();
            }
            
            if (cookie.getMaxAge() > 0) {
                cookieHeader += "; Max-Age=" + cookie.getMaxAge();
            } else if (cookie.getMaxAge() == 0) {
                cookieHeader += "; Max-Age=0";
            }
            
            cookieHeader += "; SameSite=" + sameSite;
            
            response.addHeader("Set-Cookie", cookieHeader);
        }
    }
}
