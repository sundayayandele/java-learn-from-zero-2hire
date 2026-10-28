#!/bin/sh
# Nightly backup of itlearning.tech (itl-backup.timer, 01:30 UTC), verified, newest 7 kept, with
# metrics for node-exporter's textfile collector so Grafana alerts when a backup fails or is missing.
# Runs as root on the Moodle server. Restore steps: ops/monitoring/README.md.
set -u

ROOT=/root/backups/nightly
KEEP=7
METRICS=/var/lib/node_exporter/textfile/itl_backup.prom
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
