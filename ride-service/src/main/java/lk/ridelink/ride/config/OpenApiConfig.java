package lk.ridelink.ride.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI setup.
 *
 * <p>Swagger UI is one of the two official clients for this project (the other being the
 * Postman collection), so registering the {@code bearerAuth} scheme matters: it is what
 * puts the <em>Authorize</em> button on the page, which is how a marker logs in and
 * exercises the protected endpoints during the demo.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI rideServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideLink - Ride Management Service API")
                        .version("1.0.0")
                        .description("""
                                Driver supply for RideLink: profiles, vehicles, availability,                                 simulated location and the driver-matching rule.

                                Endpoints under /api/v1/internal/** require a SERVICE token                                 (obtained from the Account service's /auth/service-token) and                                 are called only by the Ride service. A passenger or driver                                 token gets 403 there - that refusal is intentional and is                                 demonstrated as a negative case in the Postman collection.

                                All errors share one RFC 7807 shape with a machine-readable                                 `code` field; DRIVER_NOT_ELIGIBLE additionally carries a                                 `reasons` array.""")
                        .license(new License().name("IT3130 coursework, SLIIT")))
                .servers(List.of(new Server().url("http://localhost:8083").description("Local")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken returned by /api/v1/auth/login")));
    }
}
