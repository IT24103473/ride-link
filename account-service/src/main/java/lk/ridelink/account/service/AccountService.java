package lk.ridelink.account.service;

import java.util.UUID;
import lk.ridelink.account.domain.AccountStatus;
import lk.ridelink.account.domain.Role;
import lk.ridelink.account.dto.AccountResponse;
import lk.ridelink.account.dto.ChangePasswordRequest;
import lk.ridelink.account.dto.ChangeStatusRequest;
import lk.ridelink.account.dto.UpdateProfileRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Account profile reads and writes, including the admin-only operations. */
public interface AccountService {

    AccountResponse getOwnProfile();

    AccountResponse updateOwnProfile(UpdateProfileRequest request);

    void changeOwnPassword(ChangePasswordRequest request);

    /** Readable by an admin, or by the account holder themselves. Otherwise 403. */
    AccountResponse getById(UUID accountId);

    Page<AccountResponse> search(Role role, AccountStatus status, Pageable pageable);

    /** Admin only. Publishes {@code account.status.changed} when the status actually moves. */
    AccountResponse changeStatus(UUID accountId, ChangeStatusRequest request);
}
