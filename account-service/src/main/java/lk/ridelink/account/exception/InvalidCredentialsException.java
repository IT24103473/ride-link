package lk.ridelink.account.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised for both an unknown e-mail and a wrong password, with the same message either
 * way. Distinguishing them would let an attacker enumerate registered e-mail addresses.
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED,
                ErrorCodes.INVALID_CREDENTIALS,
                "Invalid credentials",
                "Email or password is incorrect");
    }
}
