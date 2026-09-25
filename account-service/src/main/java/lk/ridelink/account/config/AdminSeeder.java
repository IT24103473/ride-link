package lk.ridelink.account.config;

import lk.ridelink.account.domain.Account;
import lk.ridelink.account.domain.Role;
import lk.ridelink.account.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the first admin at startup, because there is no endpoint that can: registration
 * only ever produces PASSENGER or DRIVER accounts, and changing a status requires an
 * admin to already exist.
 *
 * <p>Credentials come from {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} in the
 * environment, never from a committed file.</p>
 */
@Configuration
public class AdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    @Bean
    public ApplicationRunner seedAdminAccount(AccountRepository accountRepository,
                                              PasswordEncoder passwordEncoder,
                                              RideLinkProperties properties) {
        return args -> {
            String email = Account.normaliseEmail(properties.admin().email());
            String password = properties.admin().password();

            // No password configured means "do not seed", which is what CI and the unit
            // tests rely on.
            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                log.info("ADMIN_EMAIL/ADMIN_PASSWORD not set; skipping admin seeding");
                return;
            }

            // Only create when absent. Without this check, restarting the service would
            // silently reset a password the admin had deliberately changed.
            if (accountRepository.existsByEmail(email)) {
                log.info("Admin account {} already exists; leaving it untouched", email);
                return;
            }

            Account admin = Account.create(
                    "RideLink Admin",
                    email,
                    "+94110000000",
                    passwordEncoder.encode(password),
                    Role.ADMIN);
            accountRepository.save(admin);

            log.info("Seeded admin account {} (id={})", email, admin.getId());
        };
    }
}
