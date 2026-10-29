#!/bin/sh
# Install or update the learner funnel metrics on the Moodle server. From the repository root:
#
#   ssh itlearning 'mkdir -p /tmp/metrics/systemd'
#   scp scripts/moodle/metrics/*.php scripts/moodle/metrics/*.sh itlearning:/tmp/metrics/
#   scp scripts/moodle/metrics/systemd/* itlearning:/tmp/metrics/systemd/
#   ssh itlearning 'sh /tmp/metrics/install.sh'
#
# The graduate survey is created once with create_graduate_survey.php (see its header).
set -eu
SRC=$(cd "$(dirname "$0")" && pwd)
mkdir -p /opt/itlearning-tech/metrics
install -m 644 "$SRC/funnel.php" /opt/itlearning-tech/metrics/
install -m 755 "$SRC/itl-funnel.sh" /usr/local/sbin/itl-funnel
install -m 644 "$SRC/systemd/itl-funnel.service" "$SRC/systemd/itl-funnel.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable -q --now itl-funnel.timer
/usr/local/sbin/itl-funnel
echo "Installed; metrics in /var/lib/node_exporter/textfile/itl_funnel.prom"
