#!/bin/sh
# Restore drill (or a real restore): bring up Moodle from a decrypted nightly backup in /opt/restore-drill.
# Run as root on the restore server (the drill uses the Jobe VPS). Steps and timings: ops/monitoring/README.md.
#   1. Decrypt the newest off-site copy into /opt/restore-drill (needs the operator's age key, see README).
#   2. Rebuild the Moodle image from the backed-up build context (~12 minutes):
#        mkdir -p /opt/restore-drill/opt && tar xzf /opt/restore-drill/2*/config.tar.gz -C /opt/restore-drill/opt
#        docker network create drillnet
#        docker buildx create --name drill --driver docker-container --driver-opt network=drillnet
#        docker buildx build --builder drill --load -t itl-drill-moodle:latest /opt/restore-drill/opt/itlearning-tech/moodle
#      (On the Jobe VPS, builds on the default bridge are blocked by jobe-egress-block; drillnet isn't.)
#   3. sh restore-drill.sh        database, moodledata and a scratch site on 127.0.0.1:8099 (~1 minute)
#   4. sh restore-drill.sh down   delete everything, including the decrypted backup
# The scratch site has no cron container, never sends email and uses its own wwwroot. The backed-up .env
# is read by Compose only; this script never handles the passwords. For a real restore, use the
# production compose file and config.php instead.
set -eu
cd /opt/restore-drill
B=$(ls -d 2* | sort | tail -1)
if [ "${1:-}" = down ]; then
    docker compose -p itldrill down -v --remove-orphans
    docker buildx rm drill || true
    docker image rm itl-drill-moodle:latest || true
    docker network rm drillnet || true
    cd / && rm -rf /opt/restore-drill
    exit 0
fi
# Compose reads the backed-up .env itself (as in production); this script never handles the passwords.
cp opt/itlearning-tech/.env .env && chmod 600 .env
# config.php as backed up, re-pointed at the scratch site; email off.
sed -e "s#^\$CFG->wwwroot .*#\$CFG->wwwroot   = 'http://127.0.0.1:8099';#" \
    -e "s#^\$CFG->sslproxy .*#\$CFG->sslproxy = false;#" \
    -e "s#^\$CFG->routerconfigured.*#&\n\$CFG->noemailever = true;#" \
    opt/itlearning-tech/moodle-config/config.php > config.php
grep -q noemailever config.php || sed -i "s#^require_once#\$CFG->noemailever = true;\n&#" config.php
chmod 644 config.php
cat > compose.yml <<'EOF'
name: itldrill
networks: {default: {name: drillnet, external: true}}
services:
  mariadb:
    image: mariadb:11.4
    command: --innodb-buffer-pool-size=256M --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    environment: {MARIADB_DATABASE: moodle, MARIADB_USER: moodleuser, MARIADB_PASSWORD: "${MOODLE_DB_PASSWORD}", MARIADB_ROOT_PASSWORD: "${MARIADB_ROOT_PASSWORD}"}
    volumes: [db:/var/lib/mysql]
  moodle:
    image: itl-drill-moodle:latest
    depends_on: [mariadb]
    ports: ["127.0.0.1:8099:80"]
    volumes: [data:/var/moodledata, ./config.php:/var/www/html/config.php:ro]
volumes: {db: {}, data: {}}
EOF
t() { echo "[$(( $(date +%s) - T0 ))s] $*"; }
T0=$(date +%s)
docker compose -p itldrill up -d mariadb
db() { docker compose -p itldrill exec -T mariadb sh -c 'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$@"' sh "$@"; }
until db -e 'SELECT 1' moodle >/dev/null 2>&1; do sleep 2; done
t "database server up"
zcat "$B/moodle.sql.gz" | db moodle
t "database loaded"
docker compose -p itldrill run --rm --no-deps -v "$PWD/$B:/in:ro" --entrypoint sh moodle -c \
    'tar xzf /in/moodledata.tar.gz -C /var/moodledata && chown -R www-data:www-data /var/moodledata'
t "moodledata restored"
docker compose -p itldrill up -d moodle
until curl -fs -o /dev/null http://127.0.0.1:8099/login/index.php; do sleep 2; done
t "Moodle answering"
