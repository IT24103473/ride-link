package lk.ridelink.account.config;

import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import lk.ridelink.account.exception.ErrorCodes;
import lk.ridelink.account.exception.GlobalExceptionHandler;
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
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Security for the Account Service.
 *
 * <p>This service is unusual in that it both <em>issues</em> tokens (so it needs a
 * {@link JwtEncoder}) and <em>verifies</em> them like every other service. The other
 * three services have a near-identical class without the encoder.</p>
 *
 * <p>That duplication is deliberate. A shared security module would couple all four
 * services to one release cycle, which defeats the point of splitting them up; the
 * trade-off is discussed in the report.</p>
 */
@Configuration
@EnableMethodSecurity   // turns on @PreAuthorize
public class SecurityConfig {

    /** Minimum key length for HS256; a shorter key is rejected outright by Nimbus. */
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
                // No cookies and no session are used, so there is no CSRF vector to protect.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Registration, login and service tokens must be reachable without a token.
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
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

    /**
     * Maps the custom {@code role} claim onto a Spring Security authority.
     *
     * <p>Without this, {@code @PreAuthorize("hasRole('ADMIN')")} would never match,
     * because Spring's default converter reads {@code scope}/{@code scp}, not
     * {@code role}. {@link SimpleAuthorityMapper} adds the {@code ROLE_} prefix that
     * {@code hasRole} expects.</p>
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** Verifies incoming tokens locally - no network call to any other service. */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /** Signs outgoing tokens. Only the Account Service has this bean. */
    @Bean
    public JwtEncoder jwtEncoder() {
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(
                new com.nimbusds.jose.jwk.JWKSet(new OctetSequenceKey.Builder(secretKey()).build()));
        return new NimbusJwtEncoder(jwkSource);
    }

    private SecretKeySpec secretKey() {
        String secret = properties.security().jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            // Fail at startup rather than issuing tokens nobody can verify.
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** BCrypt at the default strength of 10: slow enough to matter, fast enough to demo. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * 401 handler. Without this, a missing or expired token produces an empty body,
     * which would be the one response in the system not shaped like a ProblemDetail.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(MappingJackson2HttpMessageConverter converter) {
        return (request, response, authException) -> writeProblem(response, converter,
                HttpStatus.UNAUTHORIZED, "Unauthenticated",
                "A valid bearer token is required to access this resource",
                ErrorCodes.UNAUTHENTICATED, request.getRequestURI());
    }

    /** 403 handler, for a valid token whose role is not sufficient. */
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
