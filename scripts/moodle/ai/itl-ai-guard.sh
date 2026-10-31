#!/bin/sh
# Keep AI out of quizzes and assignments (block_ai_in_assessments.php). Installed as /usr/local/sbin/itl-ai-guard
# by install.sh and run daily by itl-ai-guard.timer, so new assessments are covered by the next morning.
# Run it by hand after adding assessments, or with --check to report without changing anything.
set -eu
docker cp /opt/itlearning-tech/ai/block_ai_in_assessments.php moodle:/tmp/block_ai_in_assessments.php
docker exec -u www-data moodle php /tmp/block_ai_in_assessments.php "$@"
