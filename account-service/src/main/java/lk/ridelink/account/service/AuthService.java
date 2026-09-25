package lk.ridelink.account.service;

import lk.ridelink.account.domain.Role;
import lk.ridelink.account.dto.AccountResponse;
import lk.ridelink.account.dto.LoginRequest;
import lk.ridelink.account.dto.LoginResponse;
import lk.ridelink.account.dto.RegisterRequest;
import lk.ridelink.account.dto.ServiceTokenRequest;
import lk.ridelink.account.dto.ServiceTokenResponse;

/**
 * Registration and token issuing.
 *
 * <p>Controllers depend on this interface rather than the implementation, so the web
 * layer can be tested against a mock and the implementation can be replaced without
 * touching a controller.</p>
 */
public interface AuthService {

    /**
     * Registers a new account.
     *
     * @param role decided by the caller (i.e. by which endpoint was hit), never by the
     *             request body, so nobody can register themselves as an admin
     */
    AccountResponse register(RegisterRequest request, Role role);

    LoginResponse login(LoginRequest request);

    /** Client-credentials grant; issues a {@code role=SERVICE} token. */
    ServiceTokenResponse issueServiceToken(ServiceTokenRequest request);
}
