#!/bin/sh
# Make the Jobe VPS the off-site store for itlearning.tech's nightly backups.
# Run as root on the Jobe VPS (a different provider and country from the Moodle server):
#
#   MOODLE_KEY='ssh-ed25519 AAAA... itl-backup@itlearning' sh setup-backup-receiver.sh
#
# MOODLE_KEY is /etc/itl-backup/id_ed25519.pub from the Moodle server (setup-monitoring.sh makes it).
#
# The Moodle server pushes one age-encrypted tar a night. Its key can only *add* a backup:
#   * the key is forced to itl-backup-receive and only accepted from the Moodle server's address;
#   * the receiver refuses to overwrite, can't list, read or delete, and stops when the disk is low,
#     so a compromised Moodle server can't wipe or read the off-site copies;
#   * this server never holds the decryption key (it stays with the operator), so the copies are
#     safe here even though Jobe runs learners' code.
# Retention (itl-backup-prune.timer): the 14 newest, plus the newest of each of the last 6 months.
set -eu

: "${MOODLE_KEY:?set MOODLE_KEY to the backup public key from the Moodle server}"
MOODLE_IP=${MOODLE_IP:-187.127.78.116}
DIR=/srv/itl-backups/nightly

echo "== Backup user"
id itlbackup >/dev/null 2>&1 || useradd --system --home-dir /srv/itl-backups --shell /bin/sh itlbackup
mkdir -p "$DIR" /srv/itl-backups/.ssh
chown itlbackup:itlbackup /srv/itl-backups "$DIR"
chmod 700 /srv/itl-backups "$DIR"

cat > /usr/local/bin/itl-backup-receive <<'EOF'
#!/bin/sh
# Forced command for the Moodle server's backup key (managed by setup-backup-receiver.sh).
#   put YYYYMMDD-HHMM    store stdin as that backup; prints the stored file's SHA-256
#   check YYYYMMDD-HHMM  print the SHA-256 of a stored backup
# Nothing else: no listing, reading or deleting.
set -eu
DIR=/srv/itl-backups/nightly
set -- ${SSH_ORIGINAL_COMMAND:-}
[ $# -eq 2 ] && echo "$2" | grep -Eq '^[0-9]{8}-[0-9]{4}$' || { echo "usage: put|check YYYYMMDD-HHMM" >&2; exit 2; }
file="$DIR/$2.tar.age"
case "$1" in
    check) [ -f "$file" ] || { echo "no such backup" >&2; exit 3; }; sha256sum "$file" | cut -d' ' -f1 ;;
    put)
        [ -e "$file" ] && { echo "backup $2 already exists" >&2; exit 3; }
        [ "$(df --output=avail -B1G "$DIR" | tail -1)" -ge 50 ] || { echo "less than 50 GB free" >&2; exit 4; }
        tmp=$(mktemp "$DIR/.incoming.XXXXXX")
        trap 'rm -f "$tmp"' EXIT
        head -c 20G > "$tmp"        # one backup is ~50 MB; cap it so a runaway stream can't fill the disk
        chmod 400 "$tmp"
        mv "$tmp" "$file"
        trap - EXIT
        sha256sum "$file" | cut -d' ' -f1 ;;
    *) echo "usage: put|check YYYYMMDD-HHMM" >&2; exit 2 ;;
esac
EOF
chmod 755 /usr/local/bin/itl-backup-receive

echo "restrict,from=\"$MOODLE_IP\",command=\"/usr/local/bin/itl-backup-receive\" $MOODLE_KEY" > /srv/itl-backups/.ssh/authorized_keys
chown -R itlbackup:itlbackup /srv/itl-backups/.ssh
chmod 700 /srv/itl-backups/.ssh
chmod 600 /srv/itl-backups/.ssh/authorized_keys

echo "== Retention"
cat > /usr/local/sbin/itl-backup-prune <<'EOF'
#!/bin/sh
# Keep the 14 newest backups plus the newest of each of the last 6 months (managed by setup-backup-receiver.sh).
set -eu
cd /srv/itl-backups/nightly
ls -1 | grep -E '^[0-9]{8}-[0-9]{4}\.tar\.age$' | sort > /tmp/itl-backups.all || true
{
    tail -n 14 /tmp/itl-backups.all
    awk '{ last[substr($0, 1, 6)] = $0 } END { for (m in last) print last[m] }' /tmp/itl-backups.all | sort | tail -n 6
} | sort -u > /tmp/itl-backups.keep
comm -23 /tmp/itl-backups.all /tmp/itl-backups.keep | xargs -r rm -f --
find . -maxdepth 1 -name '.incoming.*' -mmin +360 -delete
rm -f /tmp/itl-backups.all /tmp/itl-backups.keep
EOF
chmod 755 /usr/local/sbin/itl-backup-prune
cat > /etc/systemd/system/itl-backup-prune.service <<'EOF'
[Unit]
Description=Prune old itlearning.tech off-site backups

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/itl-backup-prune
EOF
cat > /etc/systemd/system/itl-backup-prune.timer <<'EOF'
[Unit]
Description=Prune old itlearning.tech off-site backups daily

[Timer]
OnCalendar=*-*-* 05:00:00 UTC
Persistent=true

[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable -q --now itl-backup-prune.timer
echo "Done. Backups land in $DIR; the Moodle server's key can only add to it."
