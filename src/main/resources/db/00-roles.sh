#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v password="$APP_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE slotline_app LOGIN PASSWORD %L', :'password') \gexec
SQL
