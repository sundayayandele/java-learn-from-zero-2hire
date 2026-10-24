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
set -eu

DOMAIN=${DOMAIN:?set DOMAIN, for example DOMAIN=jobe.itlearning.tech}
MOODLE_IP=${MOODLE_IP:-187.127.78.116}
TZ_NAME=${TZ_NAME:-Africa/Lagos}
JOBEINABOX_COMMIT=aad6f5c91c52e8ff4bed603d54ca0fb2a1f03bb0
# Jobe master as of 3 September 2026; this JobeInABox needs runguard/rmjobedir, which is
# newer than the v2.2.2 tag.
JOBE_VERSION=bfca53c0a449815d5c228353260c37b9f6e57f3d
IMAGE=itl/jobe:bfca53c-java21

. /etc/os-release
echo "== $PRETTY_NAME, $(nproc) CPUs, $(free -h | awk '/Mem:/ {print $2}') RAM"
case "$ID" in ubuntu|debian) ;; *) echo "Unsupported OS: $ID" >&2; exit 1 ;; esac

echo "== Packages"
apt-get update -q
DEBIAN_FRONTEND=noninteractive apt-get install -y -q docker.io docker-buildx git ufw caddy curl unattended-upgrades

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
DOCKER_BUILDKIT=1 docker build -q -t "$IMAGE" \
    --build-arg TZ="$TZ_NAME" --build-arg JOBE_VERSION="$JOBE_VERSION" /opt/jobeinabox

echo "== Run Jobe on 127.0.0.1:4000"
docker rm -f jobe >/dev/null 2>&1 || true
docker run -d --name jobe --restart unless-stopped -p 127.0.0.1:4000:80 "$IMAGE" >/dev/null
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

echo "== Checks"
for _ in 1 2 3 4 5 6 7 8 9 10; do
    curl -fs http://127.0.0.1:4000/jobe/index.php/restapi/languages >/dev/null && break
    sleep 3
done
echo "Languages: $(curl -fs http://127.0.0.1:4000/jobe/index.php/restapi/languages)"
docker exec jobe javac -version
echo "Done. Point CodeRunner's Jobe server setting at https://$DOMAIN"
