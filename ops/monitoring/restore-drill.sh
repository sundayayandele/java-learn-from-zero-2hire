#!/bin/sh
# Restore drill, staging copy (ops/staging/staging.sh) or a real restore: bring up Moodle from a decrypted nightly
# backup. Run as root on the restore server (the drill and staging use the Jobe VPS). Steps and timings:
# ops/monitoring/README.md.
#   1. Decrypt the newest off-site copy into $DIR (needs the operator's age key, see README).
#   2. sh restore-drill.sh build   rebuild the Moodle image from the backed-up build context (~12 minutes)
#   3. sh restore-drill.sh         database, moodledata and a scratch site on 127.0.0.1:$PORT (~1 minute)
#   4. sh restore-drill.sh down    delete everything, including the decrypted backup
# Settings (environment): DIR (/opt/restore-drill), PROJECT (itldrill), PORT (8099), WWWROOT (http://127.0.0.1:PORT).
# The scratch site has no cron container, never sends email and uses its own wwwroot. On the Jobe VPS, builds on the
# default bridge are blocked by jobe-egress-block, so the build uses its own network. The backed-up .env is read by
# Compose only; this script never handles the passwords. For a real restore, use the production compose file and
# config.php instead.
set -eu
DIR=${DIR:-/opt/restore-drill}
export ITL_PROJECT=${PROJECT:-itldrill}
export ITL_PORT=${PORT:-8099}
export ITL_NET="${ITL_PROJECT}net"
export ITL_IMAGE="${ITL_PROJECT}-moodle:latest"
WWWROOT=${WWWROOT:-http://127.0.0.1:$ITL_PORT}
cd "$DIR"
B=$(ls -d 2* | sort | tail -1)

if [ "${1:-}" = down ]; then
    docker compose -p "$ITL_PROJECT" down -v --remove-orphans || true
    docker buildx rm "$ITL_PROJECT" || true
    docker image rm "$ITL_IMAGE" || true
    docker network rm "$ITL_NET" || true
    cd / && rm -rf "$DIR"
    exit 0
fi

if [ "${1:-}" = build ]; then
    mkdir -p opt && tar xzf "$B/config.tar.gz" -C opt
    docker network inspect "$ITL_NET" >/dev/null 2>&1 || docker network create "$ITL_NET"
    docker buildx inspect "$ITL_PROJECT" >/dev/null 2>&1 \
        || docker buildx create --name "$ITL_PROJECT" --driver docker-container --driver-opt "network=$ITL_NET"
    docker buildx build --builder "$ITL_PROJECT" --load -t "$ITL_IMAGE" opt/itlearning-tech/moodle
    exit 0
fi

# Compose reads the backed-up .env itself (as in production); this script never handles the passwords.
cp opt/itlearning-tech/.env .env && chmod 600 .env
# config.php as backed up, re-pointed at the scratch site; email off.
sed -e "s#^\$CFG->wwwroot .*#\$CFG->wwwroot   = '$WWWROOT';#" \
    -e "s#^\$CFG->sslproxy .*#\$CFG->sslproxy = false;#" \
    -e "s#^\$CFG->routerconfigured.*#&\n\$CFG->noemailever = true;#" \
    opt/itlearning-tech/moodle-config/config.php > config.php
grep -q noemailever config.php || sed -i "s#^require_once#\$CFG->noemailever = true;\n&#" config.php
chmod 644 config.php
cat > compose.yml <<'EOF'
networks: {default: {name: "${ITL_NET}", external: true}}
services:
  mariadb:
    image: mariadb:11.4
    command: --innodb-buffer-pool-size=256M --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    environment: {MARIADB_DATABASE: moodle, MARIADB_USER: moodleuser, MARIADB_PASSWORD: "${MOODLE_DB_PASSWORD}", MARIADB_ROOT_PASSWORD: "${MARIADB_ROOT_PASSWORD}"}
    volumes: [db:/var/lib/mysql]
  moodle:
    image: "${ITL_IMAGE}"
    depends_on: [mariadb]
    ports: ["127.0.0.1:${ITL_PORT}:80"]
    volumes: [data:/var/moodledata, ./config.php:/var/www/html/config.php:ro]
volumes: {db: {}, data: {}}
EOF
t() { echo "[$(( $(date +%s) - T0 ))s] $*"; }
T0=$(date +%s)
docker compose -p "$ITL_PROJECT" up -d mariadb
db() { docker compose -p "$ITL_PROJECT" exec -T mariadb sh -c 'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$@"' sh "$@"; }
until db -e 'SELECT 1' moodle >/dev/null 2>&1; do sleep 2; done
t "database server up"
zcat "$B/moodle.sql.gz" | db moodle
t "database loaded"
docker compose -p "$ITL_PROJECT" run --rm --no-deps -v "$PWD/$B:/in:ro" --entrypoint sh moodle -c \
    'tar xzf /in/moodledata.tar.gz -C /var/moodledata && chown -R www-data:www-data /var/moodledata'
t "moodledata restored"
docker compose -p "$ITL_PROJECT" up -d moodle
until curl -fs -o /dev/null "http://127.0.0.1:$ITL_PORT/login/index.php"; do sleep 2; done
t "Moodle answering"
