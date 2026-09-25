package lk.ridelink.account.mapper;

import lk.ridelink.account.domain.Account;
import lk.ridelink.account.dto.AccountResponse;
import org.springframework.stereotype.Component;

/**
 * Entity to DTO conversion.
 *
 * <p>A plain class rather than a mapping framework: it is three lines, it is trivially
 * unit-testable, and every member can explain it in the viva. Its real job is to be the
 * one place that decides what leaves the service - notably, never the password hash.</p>
 */
@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getFullName(),
                account.getEmail(),
                account.getPhone(),
                account.getRole(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
