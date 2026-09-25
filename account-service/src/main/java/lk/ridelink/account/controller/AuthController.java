package lk.ridelink.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lk.ridelink.account.domain.Role;
import lk.ridelink.account.dto.AccountResponse;
import lk.ridelink.account.dto.LoginRequest;
import lk.ridelink.account.dto.LoginResponse;
import lk.ridelink.account.dto.RegisterRequest;
import lk.ridelink.account.dto.ServiceTokenRequest;
import lk.ridelink.account.dto.ServiceTokenResponse;
import lk.ridelink.account.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints.
 *
 * <p>Holds no logic beyond HTTP mapping: it triggers validation with {@code @Valid},
 * delegates to {@link AuthService} and shapes the response. Every rule - password
 * policy, duplicate e-mail, suspended account - lives in the service layer, so it is
 * enforced no matter who calls it.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login and service tokens")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        // Depends on the interface, not the implementation.
        this.authService = authService;
    }

    @PostMapping("/register/passenger")
    @Operation(summary = "Register a passenger account",
            description = "Creates an ACTIVE passenger account. The role is fixed by this "
                    + "endpoint and cannot be set from the request body.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_REGISTERED", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<AccountResponse> registerPassenger(@Valid @RequestBody RegisterRequest request) {
        AccountResponse created = authService.register(request, Role.PASSENGER);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + created.id())).body(created);
    }

    @PostMapping("/register/driver")
    @Operation(summary = "Register a driver account",
            description = "Creates an ACTIVE driver account and publishes "
                    + "'account.driver.registered', which makes the Driver service create a "
                    + "PENDING profile shell.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_REGISTERED", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<AccountResponse> registerDriver(@Valid @RequestBody RegisterRequest request) {
        AccountResponse created = authService.register(request, Role.DRIVER);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + created.id())).body(created);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and obtain a JWT",
            description = "Returns a signed HS256 token accepted by all four services. "
                    + "An unknown e-mail and a wrong password give the same 401, so the "
                    + "endpoint cannot be used to discover registered addresses.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "ACCOUNT_SUSPENDED or ACCOUNT_DEACTIVATED", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/service-token")
    @Operation(summary = "Obtain a service-to-service token",
            description = "Client-credentials grant for internal calls. The returned token "
                    + "carries role=SERVICE, which is the only role accepted by "
                    + "/api/v1/internal/** endpoints. TTL is deliberately short.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued"),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ServiceTokenResponse> serviceToken(@Valid @RequestBody ServiceTokenRequest request) {
        return ResponseEntity.ok(authService.issueServiceToken(request));
    }
}
