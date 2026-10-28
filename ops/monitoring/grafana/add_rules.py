"""Append alert rules to grafana/provisioning/alerting/alerting.yml from a compact list.

    python3 ops/monitoring/grafana/add_rules.py <name of a rule set below>

Each rule is an instant Prometheus query (A) and a threshold (C). A rule whose uid is already in the
file is replaced in place, so re-running is safe.
"""
import pathlib
import re
import sys

ALERTING = pathlib.Path(__file__).resolve().parent / "provisioning" / "alerting" / "alerting.yml"
MONITORING = "prometheus|grafana|cadvisor|node-exporter|blackbox|nginxlog|mysqld-exporter"

RULE_SETS = {
    "quick-wins": [
        dict(uid="itl-moodle-5xx", title="Moodle error rate high", for_="5m", severity="critical",
             summary="{{ humanize $values.A.Value }}% of itlearning.tech requests failed with a 5xx error (last 10 min).",
             description="PHP, database or Moodle errors while the login page may still load. Check docker logs moodle and the database.",
             expr='100 * sum(rate(moodle_http_response_count_total{status=~"5.."}[10m])) / sum(rate(moodle_http_response_count_total[10m])) and on() (sum(increase(moodle_http_response_count_total[10m])) >= 50)',
             op="gt", value=2, window=600),
        dict(uid="itl-jobe-5xx", title="Jobe errors or overload", for_="5m", severity="warning",
             summary="Jobe returned {{ humanize $values.A.Value }} 5xx responses in the last 5 min.",
             description="Learners' Checks fail. A 503 usually means all Jobe workers are busy; other 5xx errors mean Jobe or Apache in its container is failing.",
             expr='sum(increase(caddy_http_request_duration_seconds_count{instance="jobe-vps", code=~"5.."}[5m]))',
             op="gt", value=0.5, window=300),
        dict(uid="itl-disk-trend", title="Disk will fill within 7 days", for_="1h", severity="warning",
             summary="{{ $labels.instance }}: at the growth rate of the last 6 h, the root disk fills within 7 days.",
             description="Earlier warning than 'Disk almost full'. Look for growing logs, Docker images or backups.",
             expr='predict_linear(node_filesystem_avail_bytes{mountpoint="/"}[6h], 7 * 86400)',
             op="lt", value=0, window=21600),
        dict(uid="itl-container-restart", title="Container restarted", for_="0s", severity="warning",
             summary="Container {{ $labels.name }} on the Moodle server (re)started {{ humanize $values.A.Value }} s ago.",
             description="Expected after a deployment; otherwise check docker logs for a crash. The monitoring containers are excluded.",
             expr=f'time() - max by (name) (container_start_time_seconds{{name!="", name!~"{MONITORING}"}})',
             op="lt", value=900, window=900),
        dict(uid="itl-container-oom", title="Container ran out of memory", for_="0s", severity="critical",
             summary="Container {{ $labels.name }} on the Moodle server was killed for running out of memory.",
             description="Raise its memory limit or find what grew (Moodle PHP workers, MariaDB buffers or an Ollama model).",
             expr='sum by (name) (increase(container_oom_events_total{name!=""}[15m]))',
             op="gt", value=0, window=900),
    ],
    "collectors": [
        dict(uid="itl-collector-stale", title="Monitoring collector not running", for_="5m", severity="warning",
             summary="The itl-collect timer last wrote metrics {{ humanize $values.A.Value }} s ago.",
             description="Cron, mail-queue and backup checks are blind until it runs again: systemctl status itl-collect.timer",
             expr="time() - itl_collector_last_run_timestamp_seconds",
             op="gt", value=300, window=300, nodata="Alerting"),
        dict(uid="itl-moodle-cron", title="Moodle cron stalled", for_="5m", severity="critical",
             summary="No Moodle scheduled task has started for {{ humanize $values.A.Value }} s.",
             description="Cron sends emails and issues completions and certificates. Check the moodle-cron container: docker logs moodle-cron",
             expr="time() - itl_moodle_cron_last_task_start_timestamp_seconds",
             op="gt", value=900, window=300, nodata="Alerting"),
        dict(uid="itl-moodle-tasks-failing", title="Moodle scheduled tasks failing", for_="30m", severity="warning",
             summary="{{ humanize $values.A.Value }} Moodle scheduled task(s) are failing and being retried.",
             description="See Site administration → Server → Tasks → Scheduled tasks (a fail delay above 0).",
             expr="itl_moodle_scheduled_tasks_failing",
             op="gt", value=0, window=300),
        dict(uid="itl-mail-queue", title="Mail queue backlog", for_="5m", severity="warning",
             summary="{{ humanize $values.A.Value }} emails are waiting in the mail queue.",
             description="Notifications and MFA email codes are delayed. Check docker logs mailserver and postqueue -p.",
             expr="itl_mail_queue_messages",
             op="gt", value=20, window=300),
        dict(uid="itl-mail-queue-age", title="Mail stuck in the queue", for_="5m", severity="warning",
             summary="The oldest queued email has waited {{ humanize $values.A.Value }} s.",
             description="MFA email codes expire; check docker logs mailserver for deferred deliveries.",
             expr="itl_mail_queue_oldest_age_seconds",
             op="gt", value=900, window=300),
        dict(uid="itl-backup-stale", title="Backup missing or old", for_="15m", severity="critical",
             summary="The newest successful backup is {{ humanize $values.A.Value }} hours old.",
             description="The nightly backup (itl-backup.timer, 01:30 UTC) did not succeed. Check: journalctl -u itl-backup",
             expr="(time() - itl_backup_last_success_timestamp_seconds) / 3600",
             op="gt", value=26, window=300, nodata="Alerting"),
        dict(uid="itl-backup-failed", title="Backup failed", for_="0s", severity="critical",
             summary="The last backup run failed (exit code {{ $values.A.Value }}).",
             description="Check: journalctl -u itl-backup",
             expr="itl_backup_last_exit_code",
             op="gt", value=0, window=300),
    ],
    "database": [
        dict(uid="itl-db-down", title="Database down", for_="2m", severity="critical",
             summary="mysqld-exporter can't reach MariaDB (moodle-db).",
             description="Moodle is down or failing. Check docker logs moodle-db.",
             expr="mysql_up", op="lt", value=1, window=300, nodata="Alerting"),
        dict(uid="itl-db-connections", title="Database connections near the limit", for_="10m", severity="warning",
             summary="MariaDB is using {{ humanize $values.A.Value }}% of its connections.",
             description="The limit is set by --max-connections in the itlearning-tech compose file. Raise it, or look for stuck PHP requests.",
             expr="100 * mysql_global_status_threads_connected / mysql_global_variables_max_connections",
             op="gt", value=80, window=600),
        dict(uid="itl-db-slow-queries", title="Database slow queries", for_="10m", severity="warning",
             summary="{{ humanize $values.A.Value }} queries ran longer than the slow-query threshold in the last 15 min.",
             description="Usually a missing index or a heavy report. Enable the slow query log to see which.",
             expr="increase(mysql_global_status_slow_queries[15m])",
             op="gt", value=5, window=900),
        dict(uid="itl-db-aborted", title="Database connection errors", for_="10m", severity="warning",
             summary="{{ humanize $values.A.Value }} aborted connection attempts in the last 15 min.",
             description="Failed logins or network errors to MariaDB.",
             expr="increase(mysql_global_status_aborted_connects[15m])",
             op="gt", value=10, window=900),
    ],
}


def render(r):
    q = lambda s: s.replace("'", "''")
    return f"""      - uid: {r['uid']}
        title: {r['title']}
        condition: C
        for: {r['for_']}
        noDataState: {r.get('nodata', 'OK')}
        execErrState: Error
        labels: {{severity: {r['severity']}}}
        annotations:
          summary: "{r['summary']}"
          description: "{r['description']}"
        data:
          - refId: A
            datasourceUid: prometheus
            relativeTimeRange: {{from: {r['window']}, to: 0}}
            model: {{refId: A, expr: '{q(r['expr'])}', instant: true}}
          - refId: C
            datasourceUid: __expr__
            model: {{refId: C, type: threshold, expression: A, conditions: [{{evaluator: {{type: {r['op']}, params: [{r['value']}]}}}}]}}
"""


def main():
    text = ALERTING.read_text()
    for r in RULE_SETS[sys.argv[1]]:
        block = render(r)
        pattern = re.compile(rf"      - uid: {re.escape(r['uid'])}\n.*?(?=\n      - uid: |\Z)", re.S)
        if pattern.search(text):
            text = pattern.sub(block.rstrip("\n"), text)
        else:
            text = text.rstrip("\n") + "\n\n" + block
    ALERTING.write_text(text.rstrip("\n") + "\n")
    print(f"{sys.argv[1]}: {len(RULE_SETS[sys.argv[1]])} rules written")


if __name__ == "__main__":
    main()
