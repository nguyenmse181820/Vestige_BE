# HttpOnly Cookie Authentication Implementation

## What We Implemented

Your team asked you to save 2 tokens to httpOnly cookies for better security. Here's what we implemented:

### 1. Two Token System
- **Access Token**: Short-lived token (configurable via `jwt.expiration`) for API authentication
- **Refresh Token**: Longer-lived token (configurable via `jwt.refresh-expiration`) for getting new access tokens

### 2. HttpOnly Cookies
- Both tokens are stored in **httpOnly cookies** instead of being returned in response body
- HttpOnly means JavaScript cannot access these cookies (prevents XSS attacks)
- Cookies are automatically sent with requests to your domain

### 3. Security Benefits
- **XSS Protection**: Tokens cannot be stolen via JavaScript
- **Automatic Token Management**: No need to manually manage tokens in frontend
- **CSRF Protection**: Can be enhanced with SameSite attribute
- **Secure Flag**: Ensures cookies only sent over HTTPS in production

## Files Modified/Created

### New Files:
- `src/main/java/se/vestige_be/util/CookieUtil.java` - Cookie management utility

### Modified Files:
- `src/main/java/se/vestige_be/configuration/JWTTokenUtil.java` - Added refresh token generation
- `src/main/java/se/vestige_be/configuration/JwtAuthenticationFilter.java` - Reads tokens from cookies
- `src/main/java/se/vestige_be/configuration/SecurityConfig.java` - Updated filter configuration
- `src/main/java/se/vestige_be/controller/AuthController.java` - Updated login, register, refresh, logout endpoints
- `src/main/java/se/vestige_be/dto/response/AuthResponse.java` - Removed token fields

## How It Works

### Login Process:
1. User sends credentials to `/api/auth/login`
2. Server validates credentials
3. Server generates access + refresh tokens
4. Server sets both tokens as httpOnly cookies
5. Server returns user info (no tokens in response)

### Register Process:
1. User sends registration data to `/api/auth/register`
2. Server creates new user account
3. Server generates access + refresh tokens
4. Server sets both tokens as httpOnly cookies
5. Server returns user info (no tokens in response)

### API Requests:
1. Frontend makes requests (cookies sent automatically)
2. `JwtAuthenticationFilter` reads access token from cookie
3. Token is validated and user is authenticated

### Token Refresh:
1. When access token expires, call `/api/auth/refresh`
2. Server reads refresh token from cookie
3. Server validates refresh token
4. Server generates new access + refresh tokens
5. Server updates cookies with new tokens

### Logout:
1. Call `/api/auth/logout`
2. Server clears both cookies (sets maxAge=0)

## Configuration

### Cookie Settings (in CookieUtil):
- `httpOnly: true` - Prevents JavaScript access
- `secure: configurable` - Only sent over HTTPS when enabled
- `path: "/"` for both tokens (available site-wide)
- `maxAge: token expiration time`
- `sameSite: configurable` - CSRF protection
- `domain: configurable` - Set for production domain

### Application Properties:
```yaml
# JWT Configuration
jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:900000}      # 15 minutes for access token
  refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000}  # 7 days for refresh token

# App Configuration
app:
  cookie:
    secure: ${COOKIE_SECURE:false}  # Set to true in production with HTTPS
    same-site: ${COOKIE_SAME_SITE:STRICT}  # STRICT, LAX, or NONE
    domain: ${COOKIE_DOMAIN:}  # Set your domain in production (e.g., .yourdomain.com)
```

### Environment-specific Configuration:

**Development (HTTP):**
```bash
COOKIE_SECURE=false
COOKIE_SAME_SITE=STRICT
# COOKIE_DOMAIN is empty for localhost
```

**Production (HTTPS):**
```bash
COOKIE_SECURE=true
COOKIE_SAME_SITE=STRICT
COOKIE_DOMAIN=.yourdomain.com
```

## Frontend Changes Needed

### Before (with tokens in response):
```javascript
// Login
const response = await fetch('/api/auth/login', {
  method: 'POST',
  body: JSON.stringify(credentials)
});
const data = await response.json();
localStorage.setItem('accessToken', data.accessToken); // ❌ Not secure

// API calls
fetch('/api/protected', {
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}` // ❌ Manual management
  }
});
```

### After (with httpOnly cookies):
```javascript
// Login
const response = await fetch('/api/auth/login', {
  method: 'POST',
  credentials: 'include', // ✅ Include cookies
  body: JSON.stringify(credentials)
});
const data = await response.json(); // Only user info, no tokens

// Register
const response = await fetch('/api/auth/register', {
  method: 'POST',
  credentials: 'include', // ✅ Include cookies
  body: JSON.stringify(registrationData)
});
const data = await response.json(); // Only user info, no tokens

// API calls
fetch('/api/protected', {
  credentials: 'include' // ✅ Cookies sent automatically
});

// Refresh token (when needed)
fetch('/api/auth/refresh', {
  method: 'POST',
  credentials: 'include' // ✅ Refresh token sent automatically
});

// Logout
fetch('/api/auth/logout', {
  method: 'POST',
  credentials: 'include' // ✅ Cookies cleared automatically
});
```

## Key Points

1. **Always include `credentials: 'include'`** in fetch requests
2. **No manual token management** needed in frontend
3. **Tokens are automatically sent** with requests
4. **More secure** than localStorage/sessionStorage
5. **CORS configuration** may need `allowCredentials: true`

## API Endpoint Changes

### `/api/auth/login`
- **Before**: Returns `{accessToken, refreshToken, userId, username}`
- **After**: Returns `{userId, username, email, role}` + sets httpOnly cookies

### `/api/auth/register`
- **Before**: Returns `{accessToken, refreshToken, userId, username}`
- **After**: Returns `{userId, username, email, role}` + sets httpOnly cookies

### `/api/auth/refresh`
- **Before**: Expects `{refreshToken}` in request body
- **After**: Reads refresh token from httpOnly cookie automatically

### `/api/auth/logout`
- **Before**: Expects `{refreshToken}` in request body
- **After**: Reads refresh token from cookie and clears both cookies

## Testing

You can test the implementation by:
1. Starting your application
2. Making a POST request to `/api/auth/login` with valid credentials
3. Checking browser dev tools → Application → Cookies to see the httpOnly cookies
4. Making authenticated requests to protected endpoints (cookies will be sent automatically)

This implementation provides a much more secure authentication system compared to storing tokens in localStorage or returning them in response bodies.

## Testing with cURL

```bash
# Login and save cookies to file
curl -c cookies.txt -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"your_username","password":"your_password"}'

# Use saved cookies for authenticated request
curl -b cookies.txt -X GET "http://localhost:8080/api/auth/debug/cookies"

# Refresh tokens using saved cookies
curl -b cookies.txt -c cookies.txt -X POST "http://localhost:8080/api/auth/refresh"

# Logout using saved cookies
curl -b cookies.txt -X POST "http://localhost:8080/api/auth/logout"
```
