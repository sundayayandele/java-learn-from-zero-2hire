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
    # Keep .env (the Grafana password) and replace everything else.
    find "$DEST" -mindepth 1 -maxdepth 1 ! -name .env -exec rm -rf {} +
    cp -R "$SRC"/. "$DEST"/
fi
cd "$DEST"
if [ ! -s .env ]; then
    ( umask 077; printf 'GRAFANA_ADMIN_PASSWORD=%s\n' "$(openssl rand -base64 33 | tr -dc 'A-Za-z0-9' | cut -c1-28)" > .env )
fi
chmod 600 .env

echo "== Start"
docker compose pull -q
docker compose up -d --remove-orphans
docker compose ps --format '{{.Name}} {{.Status}}'

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
