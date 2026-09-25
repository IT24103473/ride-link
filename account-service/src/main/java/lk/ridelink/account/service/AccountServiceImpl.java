package lk.ridelink.account.service;

import java.util.UUID;
import lk.ridelink.account.domain.Account;
import lk.ridelink.account.domain.AccountStatus;
import lk.ridelink.account.domain.Role;
import lk.ridelink.account.dto.AccountResponse;
import lk.ridelink.account.dto.ChangePasswordRequest;
import lk.ridelink.account.dto.ChangeStatusRequest;
import lk.ridelink.account.dto.UpdateProfileRequest;
import lk.ridelink.account.exception.BusinessRuleException;
import lk.ridelink.account.exception.InvalidCredentialsException;
import lk.ridelink.account.exception.NotFoundException;
import lk.ridelink.account.mapper.AccountMapper;
import lk.ridelink.account.messaging.AccountStatusChangedEvent;
import lk.ridelink.account.messaging.EventPublisher;
import lk.ridelink.account.messaging.EventTypes;
import lk.ridelink.account.repository.AccountRepository;
import lk.ridelink.account.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;
    private final CurrentUser currentUser;

    public AccountServiceImpl(AccountRepository accountRepository,
                              AccountMapper accountMapper,
                              PasswordEncoder passwordEncoder,
                              EventPublisher eventPublisher,
                              CurrentUser currentUser) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getOwnProfile() {
        return accountMapper.toResponse(require(currentUser.id()));
    }

    @Override
    @Transactional
    public AccountResponse updateOwnProfile(UpdateProfileRequest request) {
        Account account = require(currentUser.id());
        // Only name and phone: e-mail is the login identifier and role is an
        // authorisation decision, so neither is settable here.
        account.updateProfile(request.fullName(), request.phone());
        return accountMapper.toResponse(accountRepository.save(account));
    }

    @Override
    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request) {
        Account account = require(currentUser.id());

        // Requiring the current password means a stolen token alone is not enough to
        // take over the account permanently.
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        account.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        accountRepository.save(account);
        log.info("Password changed for account id={}", account.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getById(UUID accountId) {
        // Ownership check sits here, not in the controller: @PreAuthorize can express
        // "is an admin" but not "is this particular account".
        if (!currentUser.isAdmin() && !currentUser.is(accountId)) {
            throw new AccessDeniedException("Only an admin or the account holder may read this account");
        }
        return accountMapper.toResponse(require(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountResponse> search(Role role, AccountStatus status, Pageable pageable) {
        return accountRepository.search(role, status, pageable).map(accountMapper::toResponse);
    }

    @Override
    @Transactional
    public AccountResponse changeStatus(UUID accountId, ChangeStatusRequest request) {
        // An admin suspending themselves could lock every admin out of the system, so
        // this is refused outright rather than merely discouraged.
        if (currentUser.is(accountId)) {
            throw new BusinessRuleException("An admin cannot change their own account status");
        }

        Account account = require(accountId);
        AccountStatus oldStatus = account.getStatus();

        if (oldStatus == request.status()) {
            // Nothing moved, so publishing an event would make consumers do pointless work.
            log.debug("Account id={} already has status {}; no change published", accountId, oldStatus);
            return accountMapper.toResponse(account);
        }

        account.changeStatus(request.status());
        account = accountRepository.save(account);

        log.info("Admin changed account id={} status {} -> {} (reason: {})",
                accountId, oldStatus, request.status(), request.reason());

        // Drives the Driver service's accountActive flag, which forces a suspended
        // driver offline so they stop being matched to rides.
        eventPublisher.publish(EventTypes.ACCOUNT_STATUS_CHANGED, new AccountStatusChangedEvent(
                account.getId(),
                account.getRole().name(),
                oldStatus.name(),
                request.status().name()));

        return accountMapper.toResponse(account);
    }

    private Account require(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> NotFoundException.account(accountId));
    }
}
