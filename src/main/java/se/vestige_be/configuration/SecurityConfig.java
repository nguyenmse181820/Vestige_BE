package se.vestige_be.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import se.vestige_be.service.CustomUserDetailsService;
import se.vestige_be.util.CookieUtil;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JWTTokenUtil jwtTokenUtil;
    private final CorsConfigurationSource corsConfigurationSource;
    private final CookieUtil cookieUtil;

    public SecurityConfig(@Lazy CustomUserDetailsService userDetailsService,
                          JWTTokenUtil jwtTokenUtil,
                          CorsConfigurationSource corsConfigurationSource,
                          CookieUtil cookieUtil) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenUtil = jwtTokenUtil;
        this.corsConfigurationSource = corsConfigurationSource;
        this.cookieUtil = cookieUtil;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenUtil, userDetailsService, cookieUtil);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/stripe/webhook",
                                "/api/payos/webhook/health",
                                "/ws/**", "/chat/**",
                                "/css/**", "/js/**", "/static/**",
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/memberships/plans", "/api/memberships/health").permitAll()  // Public membership endpoints
                        .requestMatchers(HttpMethod.GET, "/api/categories/**", "/api/brands/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/{id:[0-9]+}").permitAll()  // Only numeric IDs
                        .requestMatchers(HttpMethod.GET, 
                                "/api/products",
                                "/api/products/{id:[0-9]+}",
                                "/api/products/slug/{slug}",
                                "/api/products/slug-available/{slug}",
                                "/api/products/top-viewed"
                        ).permitAll()  // Public product endpoints
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}