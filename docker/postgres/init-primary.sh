#!/bin/bash
# Runs once on primary first boot: create the replication role used by pg_basebackup.
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD 'replicator';
EOSQL
echo "host replication replicator all md5" >> "$PGDATA/pg_hba.conf"
