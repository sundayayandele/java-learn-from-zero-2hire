#!/bin/sh
# Set up the Jobe sandbox server that CodeRunner on itlearning.tech sends learners' code to.
# Run as root on a fresh Ubuntu 24.04+ (or Debian 12+) VPS dedicated to Jobe:
#
#   DOMAIN=jobe.itlearning.tech sh setup-jobe.sh
#
# Result: Jobe (JobeInABox, Java 21) listens on 127.0.0.1:4000 only. Caddy serves it at
# https://$DOMAIN with a Let's Encrypt certificate and answers only the Moodle server
# ($MOODLE_IP); ufw allows SSH, port 80 (certificate challenges) and port 443 from the
# Moodle server only. Re-running the script rebuilds and restarts Jobe.
#
# Abuse protection (anyone who can enrol on itlearning.tech can submit code):
#   * SSH accepts keys only, root by key only, and fail2ban bans repeated failures
#     (password logins are left on, with a warning, if root has no authorized key yet);
#   * the Jobe container may answer connections but never start one: no internet, and no
#     access to this host's own services (jobe-egress-block.service, re-applied at boot);
#   * the container is capped at JOBE_CPUS cores, JOBE_MEMORY RAM and JOBE_PIDS processes,
#     so a flood of submissions can't starve SSH or Caddy.
set -eu

DOMAIN=${DOMAIN:?set DOMAIN, for example DOMAIN=jobe.itlearning.tech}
MOODLE_IP=${MOODLE_IP:-187.127.78.116}
TZ_NAME=${TZ_NAME:-Africa/Lagos}
JOBEINABOX_COMMIT=aad6f5c91c52e8ff4bed603d54ca0fb2a1f03bb0
# Jobe master as of 3 September 2026; this JobeInABox needs runguard/rmjobedir, which is
# newer than the v2.2.2 tag.
JOBE_VERSION=bfca53c0a449815d5c228353260c37b9f6e57f3d
IMAGE=itl/jobe:bfca53c-java21
# Leave two cores and 40% of the RAM for the host; 16 Jobe workers x 256 processes each fit in 8192.
JOBE_CPUS=${JOBE_CPUS:-$(( $(nproc) > 3 ? $(nproc) - 2 : 1 ))}
JOBE_MEMORY=${JOBE_MEMORY:-$(free -m | awk '/Mem:/ {print int($2 * 0.6)}')m}
JOBE_PIDS=${JOBE_PIDS:-8192}

. /etc/os-release
echo "== $PRETTY_NAME, $(nproc) CPUs, $(free -h | awk '/Mem:/ {print $2}') RAM"
case "$ID" in ubuntu|debian) ;; *) echo "Unsupported OS: $ID" >&2; exit 1 ;; esac

echo "== Packages"
apt-get update -q
DEBIAN_FRONTEND=noninteractive apt-get install -y -q docker.io docker-buildx git ufw caddy curl unattended-upgrades \
    fail2ban python3-systemd

echo "== Build JobeInABox $JOBEINABOX_COMMIT with Jobe $JOBE_VERSION and Java 21"
rm -rf /opt/jobeinabox
git clone -q https://github.com/trampgeek/jobeinabox.git /opt/jobeinabox
git -C /opt/jobeinabox checkout -q "$JOBEINABOX_COMMIT"
# Match the course's Java 21 instead of the distribution's default JDK.
sed -i 's/^\([[:space:]]*\)default-jdk \\$/\1openjdk-21-jdk-headless \\/' /opt/jobeinabox/Dockerfile
grep -q 'openjdk-21-jdk-headless' /opt/jobeinabox/Dockerfile
# Build an exact Jobe commit rather than a branch or tag name. (${JOBE_VERSION} is meant
# literally here: it is the Dockerfile's build argument.)
# shellcheck disable=SC2016
sed -i 's|git clone --single-branch --branch ${JOBE_VERSION} https://github.com/trampgeek/jobe.git /var/www/html/jobe \&\&|git clone -q https://github.com/trampgeek/jobe.git /var/www/html/jobe \&\& git -C /var/www/html/jobe checkout -q ${JOBE_VERSION} \&\&|' /opt/jobeinabox/Dockerfile
# shellcheck disable=SC2016
grep -q 'checkout -q ${JOBE_VERSION}' /opt/jobeinabox/Dockerfile
# The build downloads packages through the Docker bridge, which the egress block below closes.
# Stop Jobe first, so no learner code runs while the block is lifted; it is restored after Jobe
# restarts. (Building with --network host doesn't work: the build starts Apache, and Caddy holds
# port 80.)
docker stop jobe >/dev/null 2>&1 || true
iptables -D DOCKER-USER -i docker0 -m conntrack --ctstate NEW -j DROP 2>/dev/null || true
DOCKER_BUILDKIT=1 docker build -q -t "$IMAGE" \
    --build-arg TZ="$TZ_NAME" --build-arg JOBE_VERSION="$JOBE_VERSION" /opt/jobeinabox

echo "== Run Jobe on 127.0.0.1:4000 (limits: $JOBE_CPUS CPUs, $JOBE_MEMORY RAM, $JOBE_PIDS processes)"
docker rm -f jobe >/dev/null 2>&1 || true
docker run -d --name jobe --restart unless-stopped -p 127.0.0.1:4000:80 \
    --cpus "$JOBE_CPUS" --memory "$JOBE_MEMORY" --memory-swap "$JOBE_MEMORY" --pids-limit "$JOBE_PIDS" \
    "$IMAGE" >/dev/null
# javac's JVM start-up is CPU-heavy: on this server's 2.1 GHz cores compiling even
# hello-world used 4-5 s of CPU time, over the ~3 s limit CodeRunner sends, so valid
# answers failed with "timelimit exceeded". Make each job see one processor, compile
# with the quick C1 JIT and the serial collector (about half the CPU time), and give the
# compile step (only) at least 20 s of CPU time: with 16 compiles at once each one used up
# to 7.3 s, against 2.3 s alone. Programs still run under the question's limit.
# With one processor the JVM picks the serial collector, and then C2 drops safepoint polls from
# counted loops: a daemon thread stuck in an O(n^2) loop blocked JVM exit for as long as the loop
# ran. UseCountedLoopSafepoints restores the polls (the CodeRunner questions' "Too slow" budget
# depends on it). Jobe joins the java command with spaces, so one setting can hold several flags.
docker exec jobe sed -i \
    -e "s/public string \$javac_extraflags = '';/public string \$javac_extraflags = '-J-XX:ActiveProcessorCount=1 -J-XX:TieredStopAtLevel=1 -J-XX:+UseSerialGC';/" \
    -e "s/public string \$java_extraflags = '';/public string \$java_extraflags = '-XX:ActiveProcessorCount=1 -XX:+UseCountedLoopSafepoints -XX:LoopStripMiningIter=1000';/" \
    /var/www/html/jobe/app/Config/Jobe.php
docker exec jobe grep -q "java_extraflags = '-XX:ActiveProcessorCount=1 -XX:+UseCountedLoopSafepoints" /var/www/html/jobe/app/Config/Jobe.php
docker exec jobe sed -i "/min_params_compile = array(/,/);/ s/\('cputime' *=> *\)2,/\120,/" \
    /var/www/html/jobe/app/Libraries/LanguageTask.php
docker exec jobe grep -q "javac_extraflags = '-J-XX:ActiveProcessorCount=1 -J-XX:TieredStopAtLevel=1" /var/www/html/jobe/app/Config/Jobe.php
docker exec jobe grep -q "'cputime' *=> *20," /var/www/html/jobe/app/Libraries/LanguageTask.php
docker restart jobe >/dev/null

echo "== Caddy: https://$DOMAIN, Moodle server only"
cat > /etc/caddy/Caddyfile <<EOF
$DOMAIN {
	@notmoodle not remote_ip $MOODLE_IP
	respond @notmoodle 403
	reverse_proxy 127.0.0.1:4000
}
EOF
systemctl enable -q caddy
systemctl restart caddy

echo "== Firewall"
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow OpenSSH >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow from "$MOODLE_IP" to any port 443 proto tcp >/dev/null
ufw --force enable >/dev/null
ufw status | sed -n '1,12p'

echo "== SSH: keys only"
if [ -s /root/.ssh/authorized_keys ]; then
    # sshd uses the first value it reads: this file sorts before cloud-init's 50-cloud-init.conf,
    # and the Include comes before the main config's own PermitRootLogin line.
    cat > /etc/ssh/sshd_config.d/00-itl-hardening.conf <<EOF
# Managed by setup-jobe.sh.
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
MaxAuthTries 3
EOF
    sshd -t
    systemctl reload ssh
    sshd -T | grep -E '^(passwordauthentication|permitrootlogin) '
else
    echo "WARNING: root has no authorized SSH key, so password logins stay on. Add a key, then re-run." >&2
fi

echo "== fail2ban"
cat > /etc/fail2ban/jail.d/itl-sshd.local <<EOF
# Managed by setup-jobe.sh.
[sshd]
enabled = true
backend = systemd
maxretry = 5
findtime = 10m
bantime = 1h
bantime.increment = true
bantime.maxtime = 1w
EOF
systemctl enable -q fail2ban
systemctl restart fail2ban
sleep 2
fail2ban-client status sshd | sed -n '1,4p'

echo "== Block new connections from containers"
cat > /usr/local/sbin/jobe-egress-block <<'EOF'
#!/bin/sh
# Jobe runs untrusted code: its container may answer connections but never start one.
set -eu
add() { iptables -C "$@" 2>/dev/null || iptables -I "$@"; }
add DOCKER-USER -i docker0 -m conntrack --ctstate NEW -j DROP   # to the internet or other containers
add INPUT -i docker0 -m conntrack --ctstate NEW -j DROP         # to this host's own services
EOF
chmod 755 /usr/local/sbin/jobe-egress-block
cat > /etc/systemd/system/jobe-egress-block.service <<'EOF'
[Unit]
Description=Block new connections from Docker containers (Jobe runs untrusted code)
Requires=docker.service
After=docker.service ufw.service
PartOf=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/usr/local/sbin/jobe-egress-block

[Install]
WantedBy=multi-user.target docker.service
EOF
systemctl daemon-reload
systemctl enable -q jobe-egress-block
systemctl restart jobe-egress-block

echo "== Checks"
for _ in 1 2 3 4 5 6 7 8 9 10; do
    curl -fs http://127.0.0.1:4000/jobe/index.php/restapi/languages >/dev/null && break
    sleep 3
done
echo "Languages: $(curl -fs http://127.0.0.1:4000/jobe/index.php/restapi/languages)"
docker exec jobe javac -version
# A Jobe job must not be able to open any connection: to the internet, to the Docker host
# address, or to this server's public address.
probe() {
    printf '{"run_spec": {"language_id": "python3", "sourcecode": "import socket\\ntry:\\n    socket.create_connection((\\"%s\\", %s), timeout=5)\\n    print(\\"OPEN\\")\\nexcept OSError:\\n    print(\\"blocked\\")\\n"}}' "$1" "$2" |
        curl -fs -H 'Content-Type: application/json' -d @- http://127.0.0.1:4000/jobe/index.php/restapi/runs
}
for target in "1.1.1.1 443" "172.17.0.1 22" "$(hostname -I | awk '{print $1}') 22"; do
    # shellcheck disable=SC2086
    result=$(probe $target)
    echo "A job connecting to $target: $result"
    case "$result" in *'"stdout":"blocked\n"'*) ;; *) echo "ERROR: a job could open a connection" >&2; exit 1 ;; esac
done
echo "Done. Point CodeRunner's Jobe server setting at https://$DOMAIN"
