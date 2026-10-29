#!/bin/sh
# Nightly backup of itlearning.tech (itl-backup.timer, 01:30 UTC), verified, newest 7 kept, with
# metrics for node-exporter's textfile collector so Grafana alerts when a backup fails or is missing.
# Then an off-site copy: the backup is encrypted with age to the operator's public key and pushed to
# the Jobe VPS, whose receiver can only add backups (scripts/moodle/jobe/setup-backup-receiver.sh).
# Runs as root on the Moodle server. Restore steps: ops/monitoring/README.md.
set -u

ROOT=/root/backups/nightly
KEEP=7
METRICS=/var/lib/node_exporter/textfile/itl_backup.prom
OFFSITE_METRICS=/var/lib/node_exporter/textfile/itl_backup_offsite.prom
# Off-site settings: recipients.txt (age public key), id_ed25519 (push key), known_hosts, and
# offsite.conf with OFFSITE_TARGET=itlbackup@<host>. Without them the off-site step is skipped.
OFFSITE=/etc/itl-backup
START=$(date +%s)
B="$ROOT/$(date -u +%Y%m%d-%H%M)"

write_metrics() {   # $1 = exit code
    last_ok=$(sed -n 's/^itl_backup_last_success_timestamp_seconds //p' "$METRICS" 2>/dev/null)
    size=$(sed -n 's/^itl_backup_last_success_size_bytes //p' "$METRICS" 2>/dev/null)
    if [ "$1" -eq 0 ]; then
        last_ok=$(date +%s)
        size=$(du -sb "$B" | cut -f1)
    fi
    mkdir -p "$(dirname "$METRICS")"
    cat > "$METRICS.tmp" <<EOF
# HELP itl_backup_last_success_timestamp_seconds When the last nightly backup completed and verified.
# TYPE itl_backup_last_success_timestamp_seconds gauge
itl_backup_last_success_timestamp_seconds ${last_ok:-0}
# HELP itl_backup_last_success_size_bytes Size of the last successful backup.
# TYPE itl_backup_last_success_size_bytes gauge
itl_backup_last_success_size_bytes ${size:-0}
# HELP itl_backup_last_exit_code Exit code of the last backup run (0 is success).
# TYPE itl_backup_last_exit_code gauge
itl_backup_last_exit_code $1
# HELP itl_backup_last_duration_seconds How long the last backup run took.
# TYPE itl_backup_last_duration_seconds gauge
itl_backup_last_duration_seconds $(( $(date +%s) - START ))
EOF
    chmod 644 "$METRICS.tmp"
    mv "$METRICS.tmp" "$METRICS"
}

run() {
    set -e
    mkdir -p "$B"
    chmod 700 "$ROOT" "$B"
    # Database: a consistent InnoDB snapshot; the password is read inside the container.
    docker exec moodle-db sh -c 'exec mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" --single-transaction --routines --triggers --events moodle' | gzip > "$B/moodle.sql.gz"
    zcat "$B/moodle.sql.gz" | tail -1 | grep -q 'Dump completed'
    # moodledata: uploaded files, question files, certificates.
    docker run --rm -v itlearning-tech_moodledata:/data:ro -v "$B":/out alpine:3.22 tar czf /out/moodledata.tar.gz -C /data .
    tar tzf "$B/moodledata.tar.gz" > /dev/null
    # Server configuration: the Moodle build context, compose files, .env and config.php; monitoring.
    tar czf "$B/config.tar.gz" -C /opt itlearning-tech monitoring
    tar tzf "$B/config.tar.gz" > /dev/null
    chmod 600 "$B"/*
    ( cd "$B" && sha256sum ./*.gz > SHA256SUMS )
    # Keep the newest $KEEP nightly backups.
    ls -1d "$ROOT"/2* | sort | head -n -"$KEEP" | xargs -r rm -rf
}

offsite() {
    set -e
    . "$OFFSITE/offsite.conf"
    name=$(basename "$B")
    enc="$ROOT/.offsite-$name.tar.age"
    trap 'rm -f "$enc"' EXIT
    tar cf - -C "$ROOT" "$name" | age -R "$OFFSITE/recipients.txt" > "$enc"
    want=$(sha256sum "$enc" | cut -d' ' -f1)
    got=$(timeout 1800 ssh -i "$OFFSITE/id_ed25519" -o BatchMode=yes -o IdentitiesOnly=yes \
        -o UserKnownHostsFile="$OFFSITE/known_hosts" -o StrictHostKeyChecking=yes \
        "$OFFSITE_TARGET" "put $name" < "$enc")
    [ "$got" = "$want" ] || { echo "off-site copy checksum mismatch: sent $want, stored $got" >&2; exit 1; }
    echo "off-site ok: $name ($(du -h "$enc" | cut -f1) encrypted) to ${OFFSITE_TARGET#*@}"
}

write_offsite_metrics() {   # $1 = exit code
    last_ok=$(sed -n 's/^itl_backup_offsite_last_success_timestamp_seconds //p' "$OFFSITE_METRICS" 2>/dev/null)
    [ "$1" -eq 0 ] && last_ok=$(date +%s)
    cat > "$OFFSITE_METRICS.tmp" <<EOF
# HELP itl_backup_offsite_last_success_timestamp_seconds When the last encrypted off-site copy was stored and checked.
# TYPE itl_backup_offsite_last_success_timestamp_seconds gauge
itl_backup_offsite_last_success_timestamp_seconds ${last_ok:-0}
# HELP itl_backup_offsite_last_exit_code Exit code of the last off-site copy (0 is success).
# TYPE itl_backup_offsite_last_exit_code gauge
itl_backup_offsite_last_exit_code $1
EOF
    chmod 644 "$OFFSITE_METRICS.tmp"
    mv "$OFFSITE_METRICS.tmp" "$OFFSITE_METRICS"
}

# Not "if ( run )": set -e is ignored inside an if condition, so a failing step wouldn't stop it.
( run )
code=$?
if [ "$code" -eq 0 ]; then
    write_metrics 0
    echo "backup ok: $B ($(du -sh "$B" | cut -f1))"
else
    rm -rf "$B"
    write_metrics "$code"
    echo "backup FAILED with exit code $code" >&2
    exit "$code"
fi
if [ -f "$OFFSITE/offsite.conf" ]; then
    ( offsite )
    code=$?
    write_offsite_metrics "$code"
    [ "$code" -eq 0 ] || { echo "off-site copy FAILED with exit code $code" >&2; exit "$code"; }
else
    echo "off-site copy skipped: $OFFSITE/offsite.conf is missing" >&2
fi
