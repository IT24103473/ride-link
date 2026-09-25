package lk.ridelink.account.exception;

import lk.ridelink.account.domain.AccountStatus;
import org.springframework.http.HttpStatus;

/**
 * The credentials were correct but the account may not be used. Returned as 403 rather
 * than 401 because re-authenticating would not help.
 */
public class AccountNotActiveException extends ApiException {

    public AccountNotActiveException(AccountStatus status) {
        super(HttpStatus.FORBIDDEN,
                codeFor(status),
                "Account not active",
                "This account is " + status.name().toLowerCase() + " and cannot be used");
    }

    private static String codeFor(AccountStatus status) {
        return status == AccountStatus.SUSPENDED
                ? ErrorCodes.ACCOUNT_SUSPENDED
                : ErrorCodes.ACCOUNT_DEACTIVATED;
    }
}
