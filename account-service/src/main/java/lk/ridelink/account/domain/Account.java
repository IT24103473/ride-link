package lk.ridelink.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A RideLink user account. This is the system's identity record and the only place
 * credentials are stored.
 *
 * <p>The id is a UUID persisted as {@code CHAR(36)} rather than binary, so the same
 * migration works on both MySQL and the H2 database used by the tests, and so ids are
 * readable in logs and in other services that reference them.</p>
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** Always stored lower-cased, so e-mail comparison is effectively case-insensitive. */
    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    /** BCrypt hash. Never exposed by any DTO or endpoint. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Guards against two concurrent admin updates silently overwriting each other. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Account() {
        // Required by JPA.
    }

    private Account(String fullName, String email, String phone, String passwordHash, Role role) {
        this.id = UUID.randomUUID();
        this.fullName = fullName;
        this.email = normaliseEmail(email);
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = AccountStatus.ACTIVE;
    }

    /**
     * Creates a new active account. The only way to build one, so an Account can never
     * exist without a role, a hash and a normalised e-mail.
     *
     * @param passwordHash an already-hashed password; this class never hashes, so it
     *                     cannot accidentally store a plaintext value
     */
    public static Account create(String fullName, String email, String phone,
                                 String passwordHash, Role role) {
        return new Account(fullName, email, phone, passwordHash, role);
    }

    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /** Only ACTIVE accounts may authenticate. */
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /** Updates the fields a user is allowed to change about themselves. */
    public void updateProfile(String fullName, String phone) {
        this.fullName = fullName;
        this.phone = phone;
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void changeStatus(AccountStatus newStatus) {
        this.status = newStatus;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Account account)) {
            return false;
        }
        return id != null && id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** Deliberately omits the password hash so it can never leak into a log line. */
    @Override
    public String toString() {
        return "Account{id=%s, email=%s, role=%s, status=%s}".formatted(id, email, role, status);
    }
}
