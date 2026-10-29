#!/bin/sh
# Write the learner funnel (funnel.php) as Prometheus metrics for the Grafana "Learners" dashboard.
# Installed as /usr/local/sbin/itl-funnel by install.sh and run daily by itl-funnel.timer.
# Run it by hand to see the per-cohort table: itl-funnel
set -eu
DIR=/opt/itlearning-tech/metrics
METRICS=/var/lib/node_exporter/textfile/itl_funnel.prom
docker cp "$DIR/funnel.php" moodle:/tmp/funnel.php
docker exec -u www-data moodle php /tmp/funnel.php --prom=/tmp/itl_funnel.prom
docker cp moodle:/tmp/itl_funnel.prom "$METRICS.tmp"
chmod 644 "$METRICS.tmp"
mv "$METRICS.tmp" "$METRICS"
