<?php
// Keep Moodle's AI tools out of assessments on itlearning.tech, so learners can't use them to answer quizzes or assignments.
//
// For every quiz and assignment, prohibits these capabilities for each role based on the Student archetype:
//   course assistance ("Summarise" and "Explain" on the page) and the editor's AI text and image generation.
// A prohibit at the activity can't be overridden lower down, and AI stays available on lesson pages.
// Idempotent: activities already blocked are skipped, so it is safe to run daily (itl-ai-guard.timer) and by hand.
//
// Runs inside the Moodle container (itl-ai-guard.sh copies it in):
//   php block_ai_in_assessments.php              apply to every quiz and assignment
//   php block_ai_in_assessments.php --check      report only, change nothing
define('CLI_SCRIPT', true);
require('/var/www/html/config.php');
require_once($CFG->libdir . '/clilib.php');

[$opts] = cli_get_params(['check' => false], []);
const MODULES = ['quiz', 'assign'];
const CAPABILITIES = [
    'aiplacement/courseassist:summarise_text',
    'aiplacement/courseassist:explain_text',
    'aiplacement/editor:generate_text',
    'aiplacement/editor:generate_image',
];

$capabilities = array_values(array_filter(CAPABILITIES, fn($c) => get_capability_info($c)));   // only those this Moodle has
$roles = $DB->get_records('role', ['archetype' => 'student'], '', 'id, shortname');
[$insql, $params] = $DB->get_in_or_equal(MODULES, SQL_PARAMS_NAMED);
$cms = $DB->get_records_sql("SELECT cm.id, m.name FROM {course_modules} cm JOIN {modules} m ON m.id = cm.module
                              WHERE m.name $insql", $params);

$blocked = $missing = 0;
foreach ($cms as $cm) {
    $context = context_module::instance($cm->id, IGNORE_MISSING);
    if (!$context) {
        continue;
    }
    $changed = false;
    foreach ($roles as $role) {
        foreach ($capabilities as $capability) {
            $current = $DB->get_field('role_capabilities', 'permission',
                    ['contextid' => $context->id, 'roleid' => $role->id, 'capability' => $capability]);
            if ((int)$current === CAP_PROHIBIT) {
                continue;
            }
            $changed = true;
            if (!$opts['check']) {
                assign_capability($capability, CAP_PROHIBIT, $role->id, $context->id, true);
            }
        }
    }
    $changed ? $missing++ : $blocked++;
}
$verb = $opts['check'] ? 'not yet blocked' : 'blocked now';
printf("%d quizzes and assignments: %d already blocked, %d %s (roles: %s; capabilities: %d)\n",
        count($cms), $blocked, $missing, $verb, implode(', ', array_column($roles, 'shortname')), count($capabilities));
