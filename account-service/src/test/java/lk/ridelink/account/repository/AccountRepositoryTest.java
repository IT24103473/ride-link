package lk.ridelink.account.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lk.ridelink.account.domain.Account;
import lk.ridelink.account.domain.AccountStatus;
import lk.ridelink.account.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Persistence tests against H2 in MySQL mode.
 *
 * <p>Worth having separately from the service tests because these assert things Mockito
 * cannot: that the unique index really exists, and that the filter query's
 * null-means-no-filter trick actually generates working SQL.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void seed() {
        accountRepository.saveAll(java.util.List.of(
                account("nimal@ridelink.test", Role.PASSENGER, AccountStatus.ACTIVE),
                account("kamal@ridelink.test", Role.DRIVER, AccountStatus.ACTIVE),
                account("sunil@ridelink.test", Role.DRIVER, AccountStatus.SUSPENDED),
                account("admin@ridelink.test", Role.ADMIN, AccountStatus.ACTIVE)));
        accountRepository.flush();
    }

    @Test
    @DisplayName("findByEmail: returns the account for a known address")
    void findByEmail_knownAddress_returnsAccount() {
        assertThat(accountRepository.findByEmail("nimal@ridelink.test"))
                .isPresent()
                .get()
                .extracting(Account::getRole)
                .isEqualTo(Role.PASSENGER);
    }

    @Test
    @DisplayName("findByEmail: returns empty for an unknown address")
    void findByEmail_unknownAddress_returnsEmpty() {
        assertThat(accountRepository.findByEmail("ghost@ridelink.test")).isEmpty();
    }

    @Test
    @DisplayName("the unique index rejects a second account with the same e-mail")
    void save_duplicateEmail_violatesUniqueConstraint() {
        Account duplicate = account("nimal@ridelink.test", Role.DRIVER, AccountStatus.ACTIVE);

        // This is the guarantee the application-level existsByEmail check cannot give:
        // it is what stops two simultaneous registrations both succeeding.
        assertThatThrownBy(() -> accountRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("search: no filters returns every account")
    void search_noFilters_returnsAll() {
        Page<Account> page = accountRepository.search(null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("search: filtering by role alone returns only that role")
    void search_roleOnly_filtersByRole() {
        Page<Account> page = accountRepository.search(Role.DRIVER, null, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .hasSize(2)
                .allMatch(account -> account.getRole() == Role.DRIVER);
    }

    @Test
    @DisplayName("search: filtering by status alone returns only that status")
    void search_statusOnly_filtersByStatus() {
        Page<Account> page = accountRepository.search(null, AccountStatus.SUSPENDED, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .hasSize(1)
                .allMatch(account -> account.getStatus() == AccountStatus.SUSPENDED);
    }

    @Test
    @DisplayName("search: both filters are combined with AND")
    void search_bothFilters_appliesBoth() {
        Page<Account> page = accountRepository.search(Role.DRIVER, AccountStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .hasSize(1)
                .first()
                .extracting(Account::getEmail)
                .isEqualTo("kamal@ridelink.test");
    }

    @Test
    @DisplayName("search: a filter combination that matches nothing returns an empty page")
    void search_noMatches_returnsEmptyPage() {
        Page<Account> page = accountRepository.search(Role.ADMIN, AccountStatus.SUSPENDED, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("search: pagination limits the page size while reporting the full total")
    void search_pagination_limitsPageSizeButReportsTotal() {
        Page<Account> page = accountRepository.search(null, null, PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("timestamps are populated automatically on insert")
    void save_newAccount_populatesTimestamps() {
        Account saved = accountRepository.saveAndFlush(
                account("new@ridelink.test", Role.PASSENGER, AccountStatus.ACTIVE));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    private static Account account(String email, Role role, AccountStatus status) {
        Account account = Account.create("Test User", email, "+94771234567", "$2a$10$hashed", role);
        account.changeStatus(status);
        return account;
    }
}
