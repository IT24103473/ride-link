package lk.ridelink.driver.config;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import lk.ridelink.driver.exception.ErrorCodes;
import lk.ridelink.driver.exception.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Security for the Driver &amp; Vehicle Service.
 *
 * <p>Verification only - this service holds no signing key and issues no tokens. It is a
 * near-copy of the Account Service's configuration minus the encoder, with one important
 * addition: {@code /api/v1/internal/**} is restricted to {@code ROLE_SERVICE}.</p>
 *
 * <p>That restriction is what stops a passenger reserving a driver directly, which would
 * otherwise let anyone take the whole fleet offline. It is demonstrated as a negative
 * case in the Postman collection.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final int MIN_SECRET_BYTES = 32;

    private final RideLinkProperties properties;

    public SecurityConfig(RideLinkProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuthenticationEntryPoint authenticationEntryPoint,
                                           AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Coarse gate: internal endpoints are unreachable by passengers and
                        // drivers whatever the method-level rules say, so a new internal
                        // endpoint is never left open by a forgotten annotation. ADMIN is
                        // allowed through here so read-only fleet troubleshooting works;
                        // the per-method @PreAuthorize then narrows reserve and release to
                        // SERVICE alone, since those must stay in step with the Ride service.
                        .requestMatchers("/api/v1/internal/**").hasAnyRole("SERVICE", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * Verifies tokens locally with the shared HS256 secret. No call to the Account
     * Service is made, so driver lookups keep working even if Account is down.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        String secret = properties.security().jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
        return NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(MappingJackson2HttpMessageConverter converter) {
        return (request, response, authException) -> writeProblem(response, converter,
                HttpStatus.UNAUTHORIZED, "Unauthenticated",
                "A valid bearer token is required to access this resource",
                ErrorCodes.UNAUTHENTICATED, request.getRequestURI());
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(MappingJackson2HttpMessageConverter converter) {
        return (request, response, accessDeniedException) -> writeProblem(response, converter,
                HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have permission to perform this action",
                ErrorCodes.FORBIDDEN, request.getRequestURI());
    }

    private static void writeProblem(HttpServletResponse response,
                                     MappingJackson2HttpMessageConverter converter,
                                     HttpStatus status, String title, String detail,
                                     String code, String path) throws java.io.IOException {
        ProblemDetail problem = GlobalExceptionHandler.build(status, title, detail, code, path);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        converter.getObjectMapper().writeValue(response.getOutputStream(), problem);
    }
}
