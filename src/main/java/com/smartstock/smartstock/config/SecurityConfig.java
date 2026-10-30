package com.smartstock.smartstock.config;

import com.smartstock.smartstock.security.JwtAuthFilter;
import com.smartstock.smartstock.security.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // enables @PreAuthorize on controller methods
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF disabled because REST APIs use JWT, not cookies
                // CSRF is only needed for browser session-based auth
                .csrf(AbstractHttpConfigurer::disable)

                // CORS — allow React frontend to call our API
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // STATELESS — no server sessions
                // Every request must carry its own JWT token
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(401);
                            response.getWriter().write(
                                    "{\"status\":401," +
                                            "\"error\":\"Unauthorized\"," +
                                            "\"message\":\"Authentication token is missing or invalid\"}"
                            );
                        })
                )

                // URL-level authorization rules
                .authorizeHttpRequests(auth -> auth

                        // Public endpoints — no token needed
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // Authenticated users can manage their own profile
                        .requestMatchers("/api/users/me").authenticated()

                        // Admin only endpoints
                        .requestMatchers("/api/users/**",
                                "/api/settings/**",
                                "/api/audit/**",
                                "/api/alerts/**",
                                "/api/products/export",
                                "/api/products/template")
                        .hasRole("ADMIN")

                        // Users can read and update only their own notifications
                        .requestMatchers(HttpMethod.GET, "/api/notifications/**")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MGR", "VIEWER")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/notifications/**")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MGR", "VIEWER")

                        // All authenticated roles may read warehouse lists;
                        // the service limits managers to their assigned warehouse.
                        .requestMatchers(HttpMethod.GET, "/api/warehouses/**")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MGR", "VIEWER")
                        .requestMatchers("/api/warehouses/**").hasRole("ADMIN")

                        // Inventory writes are limited to admins and managers.
                        .requestMatchers(HttpMethod.POST, "/api/stocks/movements")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MGR")
                        .requestMatchers(HttpMethod.POST, "/api/purchase-orders")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MGR")
                        .requestMatchers(HttpMethod.POST,
                                "/api/support/tickets")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MGR", "VIEWER")

                        // Every remaining read API is authenticated and read-only
                        // for managers/viewers; remaining mutations are admin-only.
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MGR", "VIEWER")
                        .requestMatchers("/api/**").hasRole("ADMIN")

                        // All other application endpoints require authentication.
                        .anyRequest().authenticated()
                )

                // Add our JWT filter BEFORE Spring's default login filter
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS configuration — who can call our API
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());

        // Allow all standard HTTP methods
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // Allow all headers including Authorization (JWT)
        config.setAllowedHeaders(List.of("*"));

        // Allow credentials (cookies, auth headers)
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        // Apply this CORS config to all endpoints
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // BCrypt password encoder — hashes passwords before saving
    // BCrypt is one-way — you cannot decrypt it
    // Interview: "How do you store passwords?"
    // Answer: "BCrypt hashing — salted, one-way, industry standard"
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // AuthenticationProvider — wires UserDetailsService + PasswordEncoder
    // This is what Spring uses to verify username + password
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // AuthenticationManager — used in login endpoint
    // to trigger the authentication process
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}