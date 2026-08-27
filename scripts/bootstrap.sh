#!/usr/bin/env bash
# ============================================================================
# RecoverAI — local toolchain bootstrap (Linux / Debian-family)
# Installs JDK 21, Maven, PostgreSQL, creates databases. Idempotent.
#   make setup  →  installs deps for all apps
#   scripts/bootstrap.sh  →  system toolchain only
# ============================================================================
set -euo pipefail

echo "── Installing system packages (requires sudo) ..."
sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
  openjdk-21-jdk-headless maven postgresql openssl curl >/dev/null

echo "── Starting PostgreSQL ..."
sudo service postgresql start || true
sleep 2

echo "── Creating role + databases ..."
sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='recoverai'" | grep -q 1 || \
  sudo -u postgres psql -c "CREATE ROLE recoverai WITH LOGIN PASSWORD 'recoverai_dev' SUPERUSER;"
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='recoverai'" | grep -q 1 || \
  sudo -u postgres psql -c "CREATE DATABASE recoverai OWNER recoverai;"
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='recoverai_test'" | grep -q 1 || \
  sudo -u postgres psql -c "CREATE DATABASE recoverai_test OWNER recoverai;"

echo "── Java:"
java -version 2>&1 | head -1
echo "── Maven:"
mvn -version 2>&1 | head -1
echo "── PostgreSQL:"
sudo -u postgres psql -c "SELECT version();" | head -2 | tail -1
echo "Bootstrap complete."
