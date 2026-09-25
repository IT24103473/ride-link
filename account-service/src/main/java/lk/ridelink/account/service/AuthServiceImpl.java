package lk.ridelink.account.service;

import java.util.Map;
import lk.ridelink.account.config.RideLinkProperties;
import lk.ridelink.account.domain.Account;
import lk.ridelink.account.domain.Role;
import lk.ridelink.account.dto.AccountResponse;
import lk.ridelink.account.dto.LoginRequest;
import lk.ridelink.account.dto.LoginResponse;
import lk.ridelink.account.dto.RegisterRequest;
import lk.ridelink.account.dto.ServiceTokenRequest;
import lk.ridelink.account.dto.ServiceTokenResponse;
import lk.ridelink.account.exception.EmailAlreadyRegisteredException;
import lk.ridelink.account.exception.InvalidCredentialsException;
import lk.ridelink.account.exception.AccountNotActiveException;
import lk.ridelink.account.mapper.AccountMapper;
import lk.ridelink.account.messaging.DriverRegisteredEvent;
import lk.ridelink.account.messaging.EventPublisher;
import lk.ridelink.account.messaging.EventTypes;
import lk.ridelink.account.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final AccountMapper accountMapper;
    private final EventPublisher eventPublisher;
    private final Map<String, String> serviceClients;

    public AuthServiceImpl(AccountRepository accountRepository,
                           PasswordEncoder passwordEncoder,
                           TokenIssuer tokenIssuer,
                           AccountMapper accountMapper,
                           EventPublisher eventPublisher,
                           RideLinkProperties properties) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.accountMapper = accountMapper;
        this.eventPublisher = eventPublisher;
        this.serviceClients = properties.serviceClients();
    }

    @Override
    @Transactional
    public AccountResponse register(RegisterRequest request, Role role) {
        String email = Account.normaliseEmail(request.email());

        // Checked up front so the common case gets a clean 409 rather than a
        // constraint-violation stack trace.
        if (accountRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        Account account = Account.create(
                request.fullName(),
                email,
                request.phone(),
                passwordEncoder.encode(request.password()),
                role);

        try {
            account = accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            // Two simultaneous registrations for the same e-mail: the unique index is
            // the real guard, and the loser lands here.
            throw new EmailAlreadyRegisteredException(email);
        }

        log.info("Registered account id={} role={}", account.getId(), role);

        if (role == Role.DRIVER) {
            // Lets the Driver service create a profile shell without polling Account.
            eventPublisher.publish(EventTypes.DRIVER_REGISTERED, new DriverRegisteredEvent(
                    account.getId(), account.getFullName(), account.getEmail(), account.getPhone()));
        }

        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Account account = accountRepository.findByEmail(Account.normaliseEmail(request.email()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            // Same exception as an unknown e-mail, so the response cannot be used to
            // discover which addresses are registered.
            throw new InvalidCredentialsException();
        }

        // Credentials are correct but the account may still be barred. Checked after the
        // password so a suspended-account response cannot confirm a guessed e-mail.
        if (!account.isActive()) {
            throw new AccountNotActiveException(account.getStatus());
        }

        TokenIssuer.IssuedToken token = tokenIssuer.issueForAccount(account);
        log.info("Login succeeded for account id={} role={}", account.getId(), account.getRole());

        return LoginResponse.of(token.value(), token.expiresInSeconds(),
                account.getRole(), account.getId());
    }

    @Override
    public ServiceTokenResponse issueServiceToken(ServiceTokenRequest request) {
        String expectedSecret = serviceClients.get(request.clientId());

        // Constant-time comparison so the endpoint does not leak the secret one
        // character at a time through response timing.
        if (expectedSecret == null || expectedSecret.isBlank()
                || !java.security.MessageDigest.isEqual(
                        expectedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        request.clientSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            log.warn("Rejected service token request for clientId={}", request.clientId());
            throw new InvalidCredentialsException();
        }

        TokenIssuer.IssuedToken token = tokenIssuer.issueForServiceClient(request.clientId());
        log.info("Issued SERVICE token to clientId={}", request.clientId());

        return ServiceTokenResponse.of(token.value(), token.expiresInSeconds());
    }
}
