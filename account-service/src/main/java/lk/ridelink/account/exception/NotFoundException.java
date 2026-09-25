package lk.ridelink.account.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "Resource not found", detail);
    }

    public static NotFoundException account(Object id) {
        return new NotFoundException("No account exists with id " + id);
    }
}
