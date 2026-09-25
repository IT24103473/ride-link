-- Account Service schema.
--
-- Portability notes (the same DDL must work on MySQL 8 and on H2 in MySQL mode):
--   * UUIDs are CHAR(36), not BINARY(16) or MySQL's UUID type.
--   * Enums are VARCHAR with a documented value set, not MySQL's ENUM type.
--   * Timestamps are DATETIME(6) and always written in UTC by the application.

CREATE TABLE account (
    id            CHAR(36)     NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    -- PASSENGER | DRIVER | ADMIN
    role          VARCHAR(20)  NOT NULL,
    -- ACTIVE | SUSPENDED | DEACTIVATED
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_account PRIMARY KEY (id),
    -- Enforces "one account per e-mail" in the database, so a race between two
    -- simultaneous registrations cannot create a duplicate.
    CONSTRAINT uk_account_email UNIQUE (email)
);

-- Supports the admin listing endpoint's role/status filters.
CREATE INDEX idx_account_role_status ON account (role, status);
