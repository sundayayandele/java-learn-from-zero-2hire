#!/bin/sh
# Install or update the AI guard on the Moodle server. From the repository root:
#
#   ssh itlearning 'mkdir -p /tmp/ai/systemd'
#   scp scripts/moodle/ai/*.php scripts/moodle/ai/*.sh itlearning:/tmp/ai/
#   scp scripts/moodle/ai/systemd/* itlearning:/tmp/ai/systemd/
#   ssh itlearning 'sh /tmp/ai/install.sh'
#
# fix_provider.php is a one-off (see its header); this installs only the daily guard and runs it once.
set -eu
SRC=$(cd "$(dirname "$0")" && pwd)
mkdir -p /opt/itlearning-tech/ai
install -m 644 "$SRC/block_ai_in_assessments.php" /opt/itlearning-tech/ai/
install -m 755 "$SRC/itl-ai-guard.sh" /usr/local/sbin/itl-ai-guard
install -m 644 "$SRC/systemd/itl-ai-guard.service" "$SRC/systemd/itl-ai-guard.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable -q --now itl-ai-guard.timer
/usr/local/sbin/itl-ai-guard
