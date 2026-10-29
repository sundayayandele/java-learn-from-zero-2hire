<?php
// Learner funnel per course and cohort for itlearning.tech (review item 19).
//
//   enrolled → active → week 4 reached → Capstone 1 passed → exam passed → certificate → course complete → job
//
// A cohort is the month of a learner's enrolment. "Week 4 reached" means the core topic quiz scheduled about four
// weeks in is complete (1.9 in Phase 1, 6.1 in Phase 2). "Job" counts graduate-survey answers that report a tech
// job, internship or trainee place (Phase 2 only).
//
// Runs inside the Moodle container (itl-funnel.sh copies it in):
//   php funnel.php                     a table per course
//   php funnel.php --prom=FILE         also write Prometheus metrics for node-exporter's textfile collector
define('CLI_SCRIPT', true);
require('/var/www/html/config.php');
require_once($CFG->libdir . '/clilib.php');
require_once($CFG->libdir . '/completionlib.php');

[$opts] = cli_get_params(['prom' => '', 'courses' => '4:1.9,5:6.1'], []);
const STAGES = ['enrolled', 'active', 'week4', 'capstone1', 'exam', 'certificate', 'complete', 'job'];
const JOB_ANSWERS = [1, 2, 3, 4];   // graduate survey "current situation": the first four options are jobs or placements

$prom = [];
foreach (explode(',', $opts['courses']) as $pair) {
    [$courseid, $week4topic] = explode(':', $pair);
    $courseid = (int)$courseid;
    $course = get_course($courseid);
    $modinfo = get_fast_modinfo($course);
    $find = function (callable $match) use ($modinfo): ?cm_info {
        foreach ($modinfo->get_cms() as $cm) { if ($match($cm)) { return $cm; } }
        return null;
    };
    $week4 = $find(fn($cm) => str_starts_with($cm->name, "$week4topic Check your understanding"));
    $cap1 = $find(fn($cm) => $cm->modname === 'assign' && str_starts_with($cm->name, 'Capstone 1 submission'));
    $exam = $find(fn($cm) => $cm->modname === 'quiz' && str_contains($cm->name, 'end-of-phase exam'));
    $cert = $find(fn($cm) => $cm->modname === 'customcert');
    $survey = $find(fn($cm) => $cm->modname === 'feedback' && str_starts_with($cm->name, 'Graduate survey'));

    $learners = $DB->get_records_sql("
        SELECT u.id,
               (SELECT MIN(CASE WHEN ue.timestart > 0 THEN ue.timestart ELSE ue.timecreated END)
                  FROM {user_enrolments} ue JOIN {enrol} e ON e.id = ue.enrolid
                 WHERE ue.userid = u.id AND e.courseid = :c1) AS planstart,
               (SELECT COUNT(1) FROM {user_lastaccess} la WHERE la.userid = u.id AND la.courseid = :c2) AS visited,
               (SELECT MAX(cc.timecompleted) FROM {course_completions} cc WHERE cc.userid = u.id AND cc.course = :c3) AS completed
          FROM {user} u
          JOIN {role_assignments} ra ON ra.userid = u.id AND ra.contextid = :ctx
               AND ra.roleid = (SELECT id FROM {role} WHERE shortname = 'student')
         WHERE u.deleted = 0", ['c1' => $courseid, 'c2' => $courseid, 'c3' => $courseid,
        'ctx' => context_course::instance($courseid)->id]);

    $state = function (?cm_info $cm, int $userid, array $ok): bool {
        global $DB;
        if (!$cm) { return false; }
        $s = $DB->get_field('course_modules_completion', 'completionstate', ['coursemoduleid' => $cm->id, 'userid' => $userid]);
        return $s !== false && in_array((int)$s, $ok);
    };
    $jobs = [];
    if ($survey) {
        $item = $DB->get_field_select('feedback_item', 'id', 'feedback = ? AND label = ?', [$survey->instance, 'situation']);
        foreach ($DB->get_records_sql("SELECT c.userid, v.value FROM {feedback_completed} c
                JOIN {feedback_value} v ON v.completed = c.id AND v.item = ? WHERE c.feedback = ?", [$item, $survey->instance]) as $r) {
            if (in_array((int)$r->value, JOB_ANSWERS)) { $jobs[$r->userid] = true; }
        }
    }

    $table = [];
    foreach ($learners as $u) {
        if (!$u->planstart) { continue; }
        $cohort = date('Y-m', $u->planstart);
        $row = &$table[$cohort];
        $row = $row ?? array_fill_keys(STAGES, 0);
        $row['enrolled']++;
        $row['active'] += $u->visited > 0;
        $row['week4'] += $state($week4, $u->id, [COMPLETION_COMPLETE, COMPLETION_COMPLETE_PASS]);
        $row['capstone1'] += $state($cap1, $u->id, [COMPLETION_COMPLETE_PASS]);
        $row['exam'] += $state($exam, $u->id, [COMPLETION_COMPLETE_PASS]);
        $row['certificate'] += $cert ? (int)$DB->record_exists('customcert_issues', ['customcertid' => $cert->instance, 'userid' => $u->id]) : 0;
        $row['complete'] += $u->completed > 0;
        $row['job'] += isset($jobs[$u->id]);
        unset($row);
    }
    ksort($table);

    echo "\n{$course->shortname}: learners reaching each stage, by enrolment month\n";
    printf("%-8s" . str_repeat('%12s', count(STAGES)) . "\n", 'cohort', ...STAGES);
    $total = array_fill_keys(STAGES, 0);
    foreach ($table as $cohort => $row) {
        printf("%-8s" . str_repeat('%12d', count(STAGES)) . "\n", $cohort, ...array_values($row));
        foreach ($row as $k => $v) { $total[$k] += $v; }
    }
    printf("%-8s" . str_repeat('%12d', count(STAGES)) . "\n", 'all', ...array_values($total));
    foreach ($total as $stage => $n) {
        $prom[] = "itl_funnel_learners{course=\"{$course->shortname}\",stage=\"$stage\"} $n";
    }
    foreach ($table as $cohort => $row) {
        $prom[] = "itl_funnel_cohort_learners{course=\"{$course->shortname}\",cohort=\"$cohort\",stage=\"enrolled\"} {$row['enrolled']}";
        $prom[] = "itl_funnel_cohort_learners{course=\"{$course->shortname}\",cohort=\"$cohort\",stage=\"complete\"} {$row['complete']}";
    }
}

if ($opts['prom']) {
    $out = "# HELP itl_funnel_learners Learners who reached each funnel stage, all cohorts.\n# TYPE itl_funnel_learners gauge\n"
        . "# HELP itl_funnel_cohort_learners Learners enrolled and complete per enrolment-month cohort.\n# TYPE itl_funnel_cohort_learners gauge\n"
        . implode("\n", $prom) . "\n";
    file_put_contents($opts['prom'] . '.tmp', $out);
    rename($opts['prom'] . '.tmp', $opts['prom']);
}
