package lk.ridelink.account.config;

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
    public OpenAPI accountServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideLink - Account Service API")
                        .version("1.0.0")
                        .description("""
                                Identity and access for RideLink. This service is the only issuer \
                                of JWTs; the other three services verify them locally.

                                To try a protected endpoint: call POST /api/v1/auth/login, copy \
                                the accessToken from the response, press Authorize above and \
                                paste it in.

                                All errors share one RFC 7807 shape with a machine-readable \
                                `code` field.""")
                        .license(new License().name("IT3130 coursework, SLIIT")))
                .servers(List.of(new Server().url("http://localhost:8081").description("Local")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken returned by /api/v1/auth/login")));
    }
}
