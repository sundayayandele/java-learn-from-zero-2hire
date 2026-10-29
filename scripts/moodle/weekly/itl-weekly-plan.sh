#!/bin/sh
# Send this week's plan to every active learner (weekly_plan.php) and record the result for Grafana.
# Installed as /usr/local/sbin/itl-weekly-plan by install.sh and run by itl-weekly-plan.timer on Monday mornings.
# Extra arguments go to weekly_plan.php, for example: itl-weekly-plan --user=42 --force
set -u
DIR=/opt/itlearning-tech/weekly
METRICS=/var/lib/node_exporter/textfile/itl_weekly_plan.prom

docker cp "$DIR/weekly_plan.php" moodle:/tmp/weekly_plan.php \
    && docker cp "$DIR/weekly-plan.json" moodle:/tmp/weekly-plan.json \
    && docker exec -u www-data moodle php /tmp/weekly_plan.php --plan=/tmp/weekly-plan.json --send "$@"
code=$?

last=$(sed -n 's/^itl_weekly_plan_last_success_timestamp_seconds //p' "$METRICS" 2>/dev/null)
[ "$code" -eq 0 ] && last=$(date +%s)
{
    echo "# HELP itl_weekly_plan_last_exit_code Exit code of the last weekly plan run (0 = OK)."
    echo "# TYPE itl_weekly_plan_last_exit_code gauge"
    echo "itl_weekly_plan_last_exit_code $code"
    if [ -n "$last" ]; then
        echo "# HELP itl_weekly_plan_last_success_timestamp_seconds When the weekly plan messages last went out."
        echo "# TYPE itl_weekly_plan_last_success_timestamp_seconds gauge"
        echo "itl_weekly_plan_last_success_timestamp_seconds $last"
    fi
} > "$METRICS.tmp" && mv "$METRICS.tmp" "$METRICS"
exit "$code"
