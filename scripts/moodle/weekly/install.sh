#!/bin/sh
# Install or update the weekly plan messages on the Moodle server. From the repository root:
#
#   python3 scripts/moodle/build_moodle.py           # refreshes lms/moodle/import/weekly-plan.json
#   scp scripts/moodle/weekly/* lms/moodle/import/weekly-plan.json itlearning:/tmp/weekly/   (with systemd/)
#   ssh itlearning 'sh /tmp/weekly/install.sh'
#
# Then try it without sending anything: ssh itlearning 'docker exec -u www-data moodle php /tmp/weekly_plan.php --plan=/tmp/weekly-plan.json'
set -eu
SRC=$(cd "$(dirname "$0")" && pwd)
mkdir -p /opt/itlearning-tech/weekly
install -m 644 "$SRC/weekly_plan.php" "$SRC/weekly-plan.json" /opt/itlearning-tech/weekly/
install -m 755 "$SRC/itl-weekly-plan.sh" /usr/local/sbin/itl-weekly-plan
install -m 644 "$SRC/systemd/itl-weekly-plan.service" "$SRC/systemd/itl-weekly-plan.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable -q --now itl-weekly-plan.timer
echo "Installed. Next run: $(systemctl show itl-weekly-plan.timer -p NextElapseUSecRealtime --value)"
