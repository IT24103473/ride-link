#!/bin/bash
# ---------------------------------------------------------------------------
# Runs once, on first MySQL container start (docker-entrypoint-initdb.d).
# Substitutes credentials from the compose environment into init.sql and
# executes it, so that no password is ever written into a committed file.
# ---------------------------------------------------------------------------
set -euo pipefail

TEMPLATE=/opt/ridelink/init.sql

for var in ACCOUNT_DB_USERNAME ACCOUNT_DB_PASSWORD \
           DRIVER_DB_USERNAME DRIVER_DB_PASSWORD \
           RIDE_DB_USERNAME RIDE_DB_PASSWORD \
           PAYMENT_DB_USERNAME PAYMENT_DB_PASSWORD; do
  if [ -z "${!var:-}" ]; then
    echo "[ridelink-init] FATAL: environment variable $var is not set." >&2
    echo "[ridelink-init] Copy .env.example to .env and fill it in." >&2
    exit 1
  fi
done

echo "[ridelink-init] Creating RideLink databases and per-service users..."

sed -e "s|\${ACCOUNT_DB_USERNAME}|${ACCOUNT_DB_USERNAME}|g" \
    -e "s|\${ACCOUNT_DB_PASSWORD}|${ACCOUNT_DB_PASSWORD}|g" \
    -e "s|\${DRIVER_DB_USERNAME}|${DRIVER_DB_USERNAME}|g" \
    -e "s|\${DRIVER_DB_PASSWORD}|${DRIVER_DB_PASSWORD}|g" \
    -e "s|\${RIDE_DB_USERNAME}|${RIDE_DB_USERNAME}|g" \
    -e "s|\${RIDE_DB_PASSWORD}|${RIDE_DB_PASSWORD}|g" \
    -e "s|\${PAYMENT_DB_USERNAME}|${PAYMENT_DB_USERNAME}|g" \
    -e "s|\${PAYMENT_DB_PASSWORD}|${PAYMENT_DB_PASSWORD}|g" \
    "$TEMPLATE" \
  | mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}"

echo "[ridelink-init] Done."
