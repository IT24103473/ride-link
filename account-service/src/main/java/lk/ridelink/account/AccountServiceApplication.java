package lk.ridelink.account;

import lk.ridelink.account.config.RideLinkProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Account Service - the only issuer of RideLink JWTs.
 *
 * <p>Owns identity data (accounts, roles, credentials) in the {@code ridelink_account}
 * database. Every other service validates the tokens this service signs, but no other
 * service may read this database.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(RideLinkProperties.class)
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
