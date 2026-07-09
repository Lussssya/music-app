#!/usr/bin/env bash
set -euo pipefail

PG_HOME="/Library/PostgreSQL/17"
PG_DATA="$PG_HOME/data"
PG_HBA="$PG_DATA/pg_hba.conf"
PSQL="$PG_HOME/bin/psql"

APP_DB="music_app"
APP_USER="music_app"
APP_PASSWORD="music_app"
POSTGRES_PASSWORD="postgres"

if [[ ! -x "$PSQL" ]]; then
  echo "PostgreSQL 17 psql was not found at $PSQL" >&2
  exit 1
fi

if [[ ! -f "$PG_HBA" ]]; then
  echo "PostgreSQL auth file was not found at $PG_HBA" >&2
  exit 1
fi

BACKUP="$PG_HBA.music-app-backup.$(date +%Y%m%d%H%M%S)"
TEMP_HBA="$(mktemp)"

restore_auth() {
  if [[ -f "$BACKUP" ]]; then
    cp "$BACKUP" "$PG_HBA"
    launchctl kickstart -k system/postgresql-17 >/dev/null 2>&1 || true
  fi
}

trap restore_auth EXIT

cp "$PG_HBA" "$BACKUP"
{
  echo "# Temporary local trust entries added by music-app setup."
  echo "local all all trust"
  echo "host all all 127.0.0.1/32 trust"
  echo "host all all ::1/128 trust"
  cat "$BACKUP"
} > "$TEMP_HBA"
cp "$TEMP_HBA" "$PG_HBA"
rm -f "$TEMP_HBA"

launchctl kickstart -k system/postgresql-17 >/dev/null
sleep 2

"$PSQL" -h localhost -U postgres -d postgres -v ON_ERROR_STOP=1 <<SQL
ALTER USER postgres WITH PASSWORD '$POSTGRES_PASSWORD';
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$APP_USER') THEN
        CREATE ROLE $APP_USER LOGIN PASSWORD '$APP_PASSWORD';
    ELSE
        ALTER ROLE $APP_USER LOGIN PASSWORD '$APP_PASSWORD';
    END IF;
END
\$\$;
SQL

if ! "$PSQL" -h localhost -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$APP_DB'" | grep -q 1; then
  "$PSQL" -h localhost -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $APP_DB OWNER $APP_USER;"
fi

"$PSQL" -h localhost -U postgres -d postgres -v ON_ERROR_STOP=1 -c "ALTER DATABASE $APP_DB OWNER TO $APP_USER;"

restore_auth
trap - EXIT

PGPASSWORD="$APP_PASSWORD" "$PSQL" -h localhost -U "$APP_USER" -d "$APP_DB" -c '\conninfo'

echo "Local PostgreSQL is ready for the music app."
