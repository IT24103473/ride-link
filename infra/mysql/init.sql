-- ---------------------------------------------------------------------------
-- RideLink database bootstrap (template).
--
-- This file is a TEMPLATE: the ${...} placeholders are substituted with values
-- from the compose environment by infra/mysql/init.sh before being executed.
-- It is never run directly, which is why no password is hard-coded here.
--
-- Data-ownership rule (CLAUDE.md section 2): each service gets its own database
-- AND its own user, and that user is granted privileges ONLY on its own
-- database. A service therefore cannot read another service's tables even by
-- accident - ownership is enforced by the database, not just by convention.
-- ---------------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS ridelink_account
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ridelink_driver
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ridelink_ride
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ridelink_payment
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Account Service -----------------------------------------------------------
CREATE USER IF NOT EXISTS '${ACCOUNT_DB_USERNAME}'@'%' IDENTIFIED BY '${ACCOUNT_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ridelink_account.* TO '${ACCOUNT_DB_USERNAME}'@'%';

-- Driver & Vehicle Service --------------------------------------------------
CREATE USER IF NOT EXISTS '${DRIVER_DB_USERNAME}'@'%' IDENTIFIED BY '${DRIVER_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ridelink_driver.* TO '${DRIVER_DB_USERNAME}'@'%';

-- Ride Management Service ---------------------------------------------------
CREATE USER IF NOT EXISTS '${RIDE_DB_USERNAME}'@'%' IDENTIFIED BY '${RIDE_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ridelink_ride.* TO '${RIDE_DB_USERNAME}'@'%';

-- Fare & Payment Service ----------------------------------------------------
CREATE USER IF NOT EXISTS '${PAYMENT_DB_USERNAME}'@'%' IDENTIFIED BY '${PAYMENT_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ridelink_payment.* TO '${PAYMENT_DB_USERNAME}'@'%';

FLUSH PRIVILEGES;
