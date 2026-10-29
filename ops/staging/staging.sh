#!/bin/sh
# On-demand staging copy of itlearning.tech (review item 18), for trying Moodle upgrades, plugin installs and big
# course changes before production. Run from the operator's Mac, which holds the backup decryption key:
#
#   sh ops/staging/staging.sh up       newest off-site backup → anonymised Moodle on the Jobe VPS (~15 minutes)
#   sh ops/staging/staging.sh status
#   sh ops/staging/staging.sh down     delete it all, including the decrypted backup
#
# The copy listens on 127.0.0.1:8098 on the Jobe VPS only; reach it with an SSH tunnel:
#   ssh -N -L 8098:127.0.0.1:8098 jobe    then open http://localhost:8098
# It has no cron and never sends email. Learners' names, emails, usernames, passwords, MFA factors, messages,
# forum posts, submissions, survey answers and logs are replaced or removed (anonymise.sql); the admin account
# stays so you can sign in as usual. Take it down when you're done: it holds a copy of production.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
HOST=${STAGING_HOST:-jobe}
KEY=${AGE_KEY:-$HOME/.config/itlearning/backup-age.key}
DIR=/opt/itl-staging
SETTINGS="DIR=$DIR PROJECT=itlstaging PORT=8098 WWWROOT=http://localhost:8098"

case "${1:-}" in
up)
    ssh "$HOST" "test ! -e $DIR" || { echo "A staging copy already exists; run: sh $0 down"; exit 1; }
    scp -q "$HERE/../monitoring/restore-drill.sh" "$HERE/anonymise.sql" "$HOST:/tmp/"
    newest=$(ssh "$HOST" 'ls -1 /srv/itl-backups/nightly/*.tar.age | sort | tail -1')
    echo "== Decrypting $newest on $HOST"
    ssh "$HOST" 'umask 077; cat > /dev/shm/itl-staging.key' < "$KEY"
    ssh "$HOST" "set -e; trap 'shred -u /dev/shm/itl-staging.key 2>/dev/null || true' EXIT
        mkdir -p $DIR && cd $DIR && age -d -i /dev/shm/itl-staging.key $newest | tar xf -
        cd 2* && sha256sum -c --quiet SHA256SUMS && echo 'checksums OK'"
    echo "== Building the Moodle image (about 12 minutes)"
    ssh "$HOST" "$SETTINGS sh /tmp/restore-drill.sh build" > /dev/null
    echo "== Restoring"
    ssh "$HOST" "$SETTINGS sh /tmp/restore-drill.sh"
    echo "== Anonymising"
    ssh "$HOST" "cd $DIR && docker compose -p itlstaging exec -T mariadb sh -c 'exec mariadb -uroot -p\"\$MARIADB_ROOT_PASSWORD\" moodle' < /tmp/anonymise.sql \
        && docker compose -p itlstaging exec -T -u www-data moodle php admin/cli/purge_caches.php && rm -f /tmp/anonymise.sql"
    echo "Staging is up. Tunnel: ssh -N -L 8098:127.0.0.1:8098 $HOST   then open http://localhost:8098"
    ;;
status)
    ssh "$HOST" "cd $DIR 2>/dev/null && docker compose -p itlstaging ps --format '{{.Service}} {{.Status}}' || echo 'no staging copy'"
    ;;
down)
    scp -q "$HERE/../monitoring/restore-drill.sh" "$HOST:/tmp/"
    ssh "$HOST" "test -e $DIR && $SETTINGS sh /tmp/restore-drill.sh down; echo 'staging removed'"
    ;;
*)
    echo "usage: sh $0 up|status|down" >&2; exit 2 ;;
esac
