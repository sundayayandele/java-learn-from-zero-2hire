#!/bin/sh
# Every minute (itl-collect.timer): Moodle cron and task health and the mail queue, written for
# node-exporter's textfile collector. Runs as root on the Moodle server.
set -eu

DIR=/var/lib/node_exporter/textfile
mkdir -p "$DIR"
TMP=$(mktemp "$DIR/.itl.prom.XXXXXX")
trap 'rm -f "$TMP"' EXIT

# One SQL query against Moodle's database, through the database container's own root credentials.
sql() {
    docker exec moodle-db sh -c 'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" -N -B moodle -e "$1"' sh "$1"
}

{
    echo "# HELP itl_moodle_cron_last_task_start_timestamp_seconds When a Moodle scheduled task last ran."
    echo "# TYPE itl_moodle_cron_last_task_start_timestamp_seconds gauge"
    echo "itl_moodle_cron_last_task_start_timestamp_seconds $(sql 'SELECT COALESCE(MAX(lastruntime), 0) FROM mdl_task_scheduled')"
    echo "# HELP itl_moodle_scheduled_tasks_failing Enabled scheduled tasks currently failing (fail delay above 0)."
    echo "# TYPE itl_moodle_scheduled_tasks_failing gauge"
    echo "itl_moodle_scheduled_tasks_failing $(sql 'SELECT COUNT(*) FROM mdl_task_scheduled WHERE faildelay > 0 AND disabled = 0')"
    echo "# HELP itl_moodle_adhoc_tasks_queued Ad hoc tasks waiting to run (certificates, emails, imports)."
    echo "# TYPE itl_moodle_adhoc_tasks_queued gauge"
    echo "itl_moodle_adhoc_tasks_queued $(sql 'SELECT COUNT(*) FROM mdl_task_adhoc')"

    # postqueue -j prints one JSON object per queued message.
    docker exec mailserver postqueue -j 2>/dev/null | python3 -c '
import json, sys, time
arrivals = [json.loads(line)["arrival_time"] for line in sys.stdin if line.strip()]
print("# HELP itl_mail_queue_messages Emails waiting in the mail server queue.")
print("# TYPE itl_mail_queue_messages gauge")
print("itl_mail_queue_messages %d" % len(arrivals))
print("# HELP itl_mail_queue_oldest_age_seconds Age of the oldest queued email (0 when the queue is empty).")
print("# TYPE itl_mail_queue_oldest_age_seconds gauge")
print("itl_mail_queue_oldest_age_seconds %d" % (time.time() - min(arrivals) if arrivals else 0))'

    echo "# HELP itl_collector_last_run_timestamp_seconds When itl-collect last completed."
    echo "# TYPE itl_collector_last_run_timestamp_seconds gauge"
    echo "itl_collector_last_run_timestamp_seconds $(date +%s)"
} > "$TMP"

chmod 644 "$TMP"
mv "$TMP" "$DIR/itl.prom"
trap - EXIT
