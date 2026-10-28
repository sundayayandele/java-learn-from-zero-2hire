#!/bin/sh
# Install or update the itlearning.tech monitoring stack on the Moodle server. Run as root:
#
#   scp -r ops/monitoring itlearning:/tmp/ && ssh itlearning sh /tmp/monitoring/setup-monitoring.sh
#
# Copies this folder to /opt/monitoring, creates .env with a random Grafana admin password on the
# first run (read it with: ssh itlearning grep GRAFANA /opt/monitoring/.env), starts the containers
# and checks that every scrape target is up and the alert rules are loaded.
set -eu

DEST=/opt/monitoring
SRC=$(cd "$(dirname "$0")" && pwd)
if [ "$SRC" != "$DEST" ]; then
    mkdir -p "$DEST"
    # Keep .env (Grafana password, alert routes) and mysqld-exporter.cnf; replace everything else.
    find "$DEST" -mindepth 1 -maxdepth 1 ! -name .env ! -name mysqld-exporter.cnf -exec rm -rf {} +
    cp -R "$SRC"/. "$DEST"/
fi
cd "$DEST"
if [ ! -s .env ]; then
    ( umask 077; printf 'GRAFANA_ADMIN_PASSWORD=%s\n' "$(openssl rand -base64 33 | tr -dc 'A-Za-z0-9' | cut -c1-28)" > .env )
fi
chmod 600 .env

echo "== Alert contact points (from .env)"
# admin@itlearning.tech always; ALERT_EMAILS adds more addresses (comma-separated); TELEGRAM_BOT_TOKEN
# and TELEGRAM_CHAT_ID add Telegram. Written as JSON, which is valid YAML, readable by Grafana only.
python3 - <<'PY'
import json, pathlib, re, sys
env = {}
for line in pathlib.Path(".env").read_text().splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        key, value = line.split("=", 1)
        env[key.strip()] = value.strip().strip('"').strip("'")
emails = ["admin@itlearning.tech"] + [e.strip() for e in env.get("ALERT_EMAILS", "").split(",") if e.strip()]
bad = [e for e in emails if not re.fullmatch(r"[^@\s,;]+@[^@\s,;]+\.[A-Za-z]{2,}", e)]
if bad:
    sys.exit("ALERT_EMAILS has invalid addresses: " + ", ".join(bad))
receivers = [{"uid": "itl-admin-email", "type": "email",
              "settings": {"addresses": ";".join(emails), "singleEmail": False}}]
token, chat = env.get("TELEGRAM_BOT_TOKEN", ""), env.get("TELEGRAM_CHAT_ID", "")
if token and chat:
    receivers.append({"uid": "itl-telegram", "type": "telegram",
                      "settings": {"bottoken": token, "chatid": chat, "parse_mode": "HTML"}})
elif token or chat:
    sys.exit("Set both TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID in .env, or neither")
config = {"apiVersion": 1,
          "contactPoints": [{"orgId": 1, "name": "ITLearning admin email", "receivers": receivers}],
          "policies": [{"orgId": 1, "receiver": "ITLearning admin email",
                        "group_by": ["grafana_folder", "alertname"], "group_wait": "30s",
                        "group_interval": "5m", "repeat_interval": "4h"}]}
out = pathlib.Path("grafana/provisioning/alerting/contactpoints.yml")
out.write_text(json.dumps(config, indent=2) + "\n")
print("  email: " + ", ".join(emails) + ("; Telegram: on" if token and chat else "; Telegram: off"))
PY
chown 472 grafana/provisioning/alerting/contactpoints.yml   # Grafana's user in its container
chmod 600 grafana/provisioning/alerting/contactpoints.yml

echo "== Nginx Proxy Manager: timing log for itlearning.tech (npm-custom/)"
# NPM's supported hooks: http_top.conf (http block) and server_proxy.conf (every proxy host).
# Install, test, and reload only if nginx accepts them; otherwise remove them again.
docker exec npm mkdir -p /data/nginx/custom
for f in http_top.conf server_proxy.conf; do
    docker cp "npm-custom/$f" "npm:/data/nginx/custom/$f"
done
if docker exec npm nginx -t >/dev/null 2>&1; then
    docker exec npm nginx -s reload
else
    docker exec npm nginx -t || true
    docker exec npm rm -f /data/nginx/custom/http_top.conf /data/nginx/custom/server_proxy.conf
    echo "ERROR: nginx rejected npm-custom/; removed it again" >&2
    exit 1
fi
sleep 2
curl -fs -o /dev/null https://itlearning.tech/login/index.php   # creates the timing log
docker exec npm sh -c 'test -s /data/logs/itl-timing_access.log && tail -1 /data/logs/itl-timing_access.log'

echo "== Database user for mysqld-exporter"
# Status only (PROCESS, REPLICATION CLIENT, performance_schema), at most 3 connections, and only from
# Docker's internal networks. Created once; the password lives only in mysqld-exporter.cnf.
if [ ! -s mysqld-exporter.cnf ]; then
    PW=$(openssl rand -base64 33 | tr -dc 'A-Za-z0-9' | cut -c1-28)
    docker exec -i moodle-db sh -c 'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"' <<SQL
CREATE USER IF NOT EXISTS 'itl_exporter'@'172.16.%' IDENTIFIED BY '$PW' WITH MAX_USER_CONNECTIONS 3;
ALTER USER 'itl_exporter'@'172.16.%' IDENTIFIED BY '$PW' WITH MAX_USER_CONNECTIONS 3;
GRANT PROCESS, REPLICATION CLIENT ON *.* TO 'itl_exporter'@'172.16.%';
GRANT SELECT ON performance_schema.* TO 'itl_exporter'@'172.16.%';
SQL
    ( umask 077; printf '[client]\nuser=itl_exporter\npassword=%s\nhost=moodle-db\nport=3306\n' "$PW" > mysqld-exporter.cnf )
    unset PW
fi
chown 65534 mysqld-exporter.cnf   # the exporter runs as nobody
chmod 400 mysqld-exporter.cnf

echo "== Start"
docker compose pull -q
docker compose up -d --remove-orphans
# Both read their configuration (Prometheus scrape jobs, Grafana alert provisioning) only at start-up.
docker compose restart prometheus grafana
docker compose ps --format '{{.Name}} {{.Status}}'

echo "== Collectors (systemd timers): cron, tasks and mail queue every minute; backup at 01:30 UTC"
mkdir -p /var/lib/node_exporter/textfile
chmod 755 collectors/*.sh
cp collectors/systemd/itl-collect.service collectors/systemd/itl-collect.timer \
    collectors/systemd/itl-backup.service collectors/systemd/itl-backup.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable -q --now itl-collect.timer itl-backup.timer
systemctl start itl-collect.service
sed -n 's/^\(itl_[a-z_]*\) /  \1 = /p' /var/lib/node_exporter/textfile/itl.prom
if ! grep -q '^itl_backup_last_success_timestamp_seconds [1-9]' /var/lib/node_exporter/textfile/itl_backup.prom 2>/dev/null; then
    echo "  no successful backup recorded yet: running one now"
    systemctl start itl-backup.service || true
    journalctl -u itl-backup -n 1 --no-pager -o cat | sed 's/^/  /'
fi

echo "== Checks"
for _ in $(seq 30); do
    curl -fs http://127.0.0.1:9090/-/ready >/dev/null && curl -fs http://127.0.0.1:3000/api/health >/dev/null && break
    sleep 2
done
sleep 45   # let every target be scraped at least once
curl -fs http://127.0.0.1:9090/api/v1/targets | python3 -c '
import json, sys
targets = json.load(sys.stdin)["data"]["activeTargets"]
for t in targets:
    print("  %-5s %-14s %s  %s" % (t["health"], t["labels"]["job"], t["labels"]["instance"], t.get("lastError", "")))
down = [t for t in targets if t["health"] != "up"]
sys.exit("%d target(s) not up" % len(down)) if down else print("  all %d targets up" % len(targets))'
GRAFANA_ADMIN_PASSWORD=$(sed -n 's/^GRAFANA_ADMIN_PASSWORD=//p' .env)
curl -fs -u "admin:$GRAFANA_ADMIN_PASSWORD" http://127.0.0.1:3000/api/v1/provisioning/alert-rules | python3 -c '
import json, sys
rules = json.load(sys.stdin)
print("  %d alert rules: %s" % (len(rules), ", ".join(r["title"] for r in rules)))'
curl -fs -u "admin:$GRAFANA_ADMIN_PASSWORD" "http://127.0.0.1:3000/api/search?type=dash-db" | python3 -c '
import json, sys
print("  dashboards: " + ", ".join(d["title"] for d in json.load(sys.stdin)))'
echo "Done. Grafana: ssh -L 3000:127.0.0.1:3000 itlearning, then http://localhost:3000 (user admin)."
