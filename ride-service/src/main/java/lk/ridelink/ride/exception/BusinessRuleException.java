package lk.ridelink.ride.exception;

import org.springframework.http.HttpStatus;

/**
 * A syntactically valid request that a domain rule forbids - for example an admin
 * trying to change their own status. 422 rather than 400: the body parsed and
 * validated fine, it is the meaning that is unacceptable.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, "Business rule violation", detail);
    }

    public BusinessRuleException(String detail) {
        this(ErrorCodes.BUSINESS_RULE_VIOLATION, detail);
    }
}
