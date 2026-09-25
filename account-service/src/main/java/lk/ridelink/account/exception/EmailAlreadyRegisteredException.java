package lk.ridelink.account.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException(String email) {
        super(HttpStatus.CONFLICT,
                ErrorCodes.EMAIL_ALREADY_REGISTERED,
                "Email already registered",
                "An account already exists for " + email);
    }
}
