package com.chatter.chatter.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.chatter.chatter.user.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * The password hasher, used both when registering and when checking a login.
     *
     * <p>Injected into {@code UserService} to hash on the way in, and used by
     * Spring's {@code AuthenticationManager} to verify. BCrypt stores its cost
     * and salt inside the hash, so raising the cost later leaves existing
     * hashes verifiable.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes Spring's configured authentication manager as a bean.
     *
     * <p>Needed because {@code AuthController.login} performs the password check
     * itself rather than going through a form-login filter; without this
     * exposure it could not be injected.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Allows the browser frontend, served from a different origin, to call this
     * API.
     *
     * <p>Origins come from {@code app.cors.allowed-origins} rather than being
     * wildcarded, which is required in any case once credentials are allowed.
     * Applies to HTTP only — the WebSocket handshake is restricted separately
     * in {@code WebSocketConfig}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * The HTTP security policy: what is public, what needs a token, and how a
     * rejection looks.
     *
     * <p>Register, login and the WebSocket handshake are open; everything else
     * requires a valid JWT, which {@link JwtAuthenticationFilter} supplies by
     * running before the username/password filter.
     *
     * <p>Stateless throughout — no session is created, so there is no session
     * cookie for CSRF to protect, which is why CSRF is off rather than
     * overlooked. Unauthenticated requests get a bare 401 instead of a redirect
     * to a login page that does not exist on this server.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless JWT API: there is no session cookie for CSRF to protect.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/api/auth/register", "/api/auth/login").permitAll()
                        // The handshake stays open; JwtChannelInterceptor rejects any
                        // STOMP CONNECT frame without a valid token.
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated())
                // Return 401 rather than a redirect to a login page that doesn't exist.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
