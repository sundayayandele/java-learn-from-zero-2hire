<?php
// End-to-end learner journey test for itlearning.tech Phase 1 (course 4), driven through Moodle's own APIs:
// sign-up age rule, policies, self-enrolment with the key, cohort approval (Phase 2), every page, all topic
// quizzes and the CodeRunner coding quiz (graded on Jobe), the exam lock and a 60-question random exam,
// submissions, rubric grading on the pass marks, certificate issue and PDF, and course completion.
// Two clearly named test learners (email off) are created; `cleanup` deletes them and all their data.
//
// Run on the Moodle server, in order, before each cohort or after course changes:
//   docker cp scripts/moodle/journey_test.php moodle:/tmp/journey.php
//   for s in setup walk grade finish cleanup; do docker exec -u www-data moodle php /tmp/journey.php $s; done
// Course-module ids below are itlearning.tech's; update them if activities are re-created.
// Policy acceptances survive privacy deletion by design; delete the test learners' rows afterwards.
define('CLI_SCRIPT', true);
require('/var/www/html/config.php');
require_once($CFG->libdir . '/completionlib.php');
require_once($CFG->libdir . '/gradelib.php');
require_once($CFG->dirroot . '/user/lib.php');
require_once($CFG->dirroot . '/cohort/lib.php');
require_once($CFG->dirroot . '/mod/quiz/locallib.php');
require_once($CFG->dirroot . '/mod/assign/locallib.php');
require_once($CFG->dirroot . '/grade/grading/lib.php');

use mod_quiz\quiz_settings;
use mod_quiz\quiz_attempt;

const COURSE = 4;
const P2COURSE = 5;
const LEARNER = 'itltest.learner1';
const LEARNER2 = 'itltest.learner2';
// Capstones submitted (certificate rule: Capstone 1 plus any two others).
const CAPSTONES = [242, 243, 244];
$failures = 0;

function say($ok, string $msg): void {
    global $failures;
    if ($ok === false) {
        $failures++;
    }
    echo ($ok === null ? '  ..   ' : ($ok ? '  ok   ' : '  FAIL ')) . $msg . "\n";
}

function learner(string $username = LEARNER) {
    global $DB;
    return $DB->get_record('user', ['username' => $username, 'deleted' => 0], '*', MUST_EXIST);
}

function as_user($user): void {
    \core\cron::setup_user($user);
}

function create_learner(string $username, string $first, string $last) {
    global $DB, $CFG;
    if ($u = $DB->get_record('user', ['username' => $username, 'deleted' => 0])) {
        return $u;
    }
    $id = user_create_user((object) [
        'username' => $username, 'firstname' => $first, 'lastname' => $last,
        'email' => "$username@example.com", 'auth' => 'manual', 'confirmed' => 1,
        'mnethostid' => $CFG->mnet_localhost_id, 'emailstop' => 1, 'country' => 'NG', 'city' => 'Lagos',
        'password' => 'Aa1!' . random_string(20),
    ], true, false);
    return $DB->get_record('user', ['id' => $id]);
}

function cm_state($cm, int $userid): int {
    $completion = new completion_info(get_course($cm->course));
    return (int) $completion->get_data($cm, false, $userid)->completionstate;
}

function attempt_quiz($cm, $user): array {
    global $DB;
    as_user($user);
    $quizobj = quiz_settings::create($cm->instance, $user->id);
    $attempts = quiz_get_user_attempts($quizobj->get_quizid(), $user->id, 'all', true);
    $last = end($attempts) ?: null;
    $attempt = quiz_prepare_and_start_new_attempt($quizobj, count($attempts) + 1, $last, false, [], [], $user->id);
    // Record each question's correct response directly in the question engine, as a form post does.
    // (Simulated responses expect multiple-choice answers as text, not as positions.)
    $quba = question_engine::load_questions_usage_by_activity($attempt->uniqueid);
    foreach ($quba->get_slots() as $slot) {
        $quba->process_action($slot, $quba->get_question($slot)->get_correct_response());
    }
    question_engine::save_questions_usage_by_activity($quba);
    $attemptobj = quiz_attempt::create($attempt->id);
    $attemptobj->process_submit(time(), false);
    $attemptobj->process_grade_submission(time());
    $attemptobj = quiz_attempt::create($attempt->id);
    $quiz = $DB->get_record('quiz', ['id' => $cm->instance]);
    $grade = quiz_rescale_grade($attemptobj->get_sum_marks(), $quiz, false);
    return [count($attemptobj->get_slots()), $grade, $quiz->grade];
}

function submit_assignment($cm, $user, string $text): bool {
    as_user($user);
    $assign = new assign(context_module::instance($cm->id), $cm, get_course($cm->course));
    $notices = [];
    $data = (object) ['onlinetext_editor' => ['text' => $text, 'format' => FORMAT_HTML,
        'itemid' => file_get_unused_draft_itemid()]];
    $saved = $assign->save_submission($data, $notices);
    $submission = $assign->get_user_submission($user->id, false);
    return $saved && $submission && $submission->status === ASSIGN_SUBMISSION_STATUS_SUBMITTED;
}

/** Grade with a rubric: $levels picks, per criterion, the level by position (0 = lowest). */
function grade_assignment($cm, $user, ?array $levels, ?float $simplegrade): array {
    $admin = get_admin();
    as_user($admin);
    $context = context_module::instance($cm->id);
    $assign = new assign($context, $cm, get_course($cm->course));
    $data = (object) ['attemptnumber' => -1, 'addattempt' => 0, 'workflowstate' => '', 'applytoall' => 0,
        'assignfeedbackcomments_editor' => ['text' => '<p>Journey test grade.</p>', 'format' => FORMAT_HTML]];
    $manager = get_grading_manager($context, 'mod_assign', 'submissions');
    $method = $manager->get_active_method();
    $expected = null;
    if ($method === 'rubric') {
        $definition = $manager->get_controller('rubric')->get_definition();
        $fill = ['criteria' => []];
        $i = 0;
        $expected = 0;
        foreach ($definition->rubric_criteria as $critid => $crit) {
            $sorted = array_values($crit['levels']);
            usort($sorted, fn($a, $b) => $a['score'] <=> $b['score']);
            $pick = $sorted[min($levels[$i] ?? end($levels), count($sorted) - 1)];
            $fill['criteria'][$critid] = ['levelid' => $pick['id'], 'remark' => ''];
            $expected += $pick['score'];
            $i++;
        }
        $data->advancedgrading = $fill;
    } else {
        $data->grade = $simplegrade;
        $expected = $simplegrade;
    }
    $assign->save_grade($user->id, $data);
    $gradinginfo = grade_get_grades($cm->course, 'mod', 'assign', $cm->instance, $user->id);
    $item = $gradinginfo->items[0];
    return [$method ?: 'simple', $expected, $item->grades[$user->id]->grade ?? null, $item->gradepass, $item->grademax];
}

function phase1_cms(int $userid = 0): array {
    $modinfo = get_fast_modinfo(COURSE, $userid);
    $out = [];
    foreach ($modinfo->get_section_info_all() as $section) {
        foreach ($modinfo->sections[$section->section] ?? [] as $cmid) {
            $out[] = $modinfo->cms[$cmid];
        }
    }
    return $out;
}

$cmd = $argv[1] ?? 'status';
\core\cron::setup_user();

if ($cmd === 'setup') {
    echo "== Sign-up rules\n";
    say(\core_auth\digital_consent::is_age_digital_consent_verification_enabled(), 'age verification is on at sign-up');
    say(\core_auth\digital_consent::is_minor(17, 'NG') === true, 'a 17-year-old in Nigeria is treated as a minor (must contact support)');
    say(\core_auth\digital_consent::is_minor(18, 'NG') === false, 'an 18-year-old in Nigeria can self-register');
    say(\core_auth\digital_consent::is_minor(16, 'GB') === true, 'the same rule applies in other countries (16 in GB is a minor)');

    echo "== Test learners\n";
    $u = create_learner(LEARNER, 'ITL Test', 'Learner One');
    $u2 = create_learner(LEARNER2, 'ITL Test', 'Learner Two');
    say(true, "created {$u->username} (id {$u->id}) and {$u2->username} (id {$u2->id}), email off");

    echo "== Policies\n";
    $versions = array_map(fn($v) => $v->id, \tool_policy\api::list_current_versions());
    as_user($u);
    \tool_policy\api::accept_policies($versions, $u->id);
    as_user($u2);
    \tool_policy\api::accept_policies($versions, $u2->id);
    \core\cron::setup_user();
    $u = learner();
    say((int) $u->policyagreed === 1, 'policies accepted by the learner (' . count($versions) . ' current policies)');

    echo "== Route 1: self-enrolment with the enrolment key (Phase 1)\n";
    $instance = $DB->get_record('enrol', ['courseid' => COURSE, 'enrol' => 'self'], '*', MUST_EXIST);
    $plugin = enrol_get_plugin('self');
    say(!empty($instance->password), 'the self-enrolment method requires a key');
    say($instance->password !== 'wrong-key', 'a wrong key does not match the course key');
    as_user($u);
    $can = $plugin->can_self_enrol($instance);
    say($can === true, 'the learner may self-enrol' . ($can === true ? '' : ': ' . $can));
    $plugin->enrol_self($instance, (object) ['enrolpassword' => $instance->password]);
    \core\cron::setup_user();
    $ctx = context_course::instance(COURSE);
    say(is_enrolled($ctx, $u->id, '', true), 'after entering the key, the learner is enrolled in Phase 1');
    say(user_has_role_assignment($u->id, $DB->get_field('role', 'id', ['shortname' => 'student']), $ctx->id),
        'with the student role');

    echo "== Route 2: approval through the cohort (Phase 2)\n";
    $cohort = $DB->get_record('cohort', ['idnumber' => 'itl-approved-p2'], '*', MUST_EXIST);
    $ctx2 = context_course::instance(P2COURSE);
    say(!is_enrolled($ctx2, $u2->id), 'before approval, learner two is not enrolled in Phase 2');
    cohort_add_member($cohort->id, $u2->id);
    say(is_enrolled($ctx2, $u2->id, '', true), "after adding them to '{$cohort->name}', they are enrolled in Phase 2");
    cohort_remove_member($cohort->id, $u2->id);
    say(!is_enrolled($ctx2, $u2->id, '', true), 'after removing them from the cohort, they lose access again');
}

if ($cmd === 'walk') {
    $u = learner();
    $completion = new completion_info(get_course(COURSE));
    $examcmid = 201;
    echo "== Before any work\n";
    $exam = get_fast_modinfo(COURSE, $u->id)->get_cm($examcmid);
    say(!$exam->available, 'the exam is locked until every topic quiz is attempted');
    $cert = get_fast_modinfo(COURSE, $u->id)->get_cm(264);
    say(!$cert->available, 'the certificate is locked');

    echo "== Pages, quizzes and submissions in course order\n";
    $counts = ['page' => 0, 'quiz' => 0, 'assign' => 0];
    $quizfail = [];
    foreach (phase1_cms($u->id) as $cm) {
        if ($cm->completion == COMPLETION_TRACKING_NONE || in_array($cm->modname, ['customcert'])) {
            continue;
        }
        if ($cm->id == $examcmid) {
            continue;   // after the topic quizzes
        }
        if ($cm->modname === 'page') {
            $completion->set_module_viewed($cm, $u->id);
            $counts['page'] += cm_state($cm, $u->id) == COMPLETION_COMPLETE;
        } else if ($cm->modname === 'quiz') {
            [$n, $grade, $max] = attempt_quiz($cm, $u);
            \core\cron::setup_user();
            $state = cm_state($cm, $u->id);
            // Topic quizzes complete after one finished attempt (COMPLETE); the coding quiz needs a pass.
            if ($grade == $max && in_array($state, [COMPLETION_COMPLETE, COMPLETION_COMPLETE_PASS])) {
                $counts['quiz']++;
            } else {
                $quizfail[] = "{$cm->name}: grade $grade/$max over $n questions, completion state $state";
            }
        } else if ($cm->modname === 'assign') {
            $iscapstone = str_starts_with($cm->name, 'Capstone ') && str_contains($cm->name, 'submission');
            if ($iscapstone && !in_array($cm->id, CAPSTONES)) {
                continue;
            }
            $ok = submit_assignment($cm, $u, '<p>Journey test submission: https://github.com/example/journey-test</p>');
            \core\cron::setup_user();
            $counts['assign'] += $ok;
            if (!$ok) {
                say(false, "submission failed: {$cm->name}");
            }
        }
    }
    say($counts['page'] == 48, "pages viewed and complete: {$counts['page']} of 48");
    say(!$quizfail, "quizzes passed with full marks: {$counts['quiz']} (topic quizzes and the coding quiz)");
    foreach ($quizfail as $f) {
        say(false, $f);
    }
    say($counts['assign'] >= 10, "assignments submitted: {$counts['assign']} (4 projects, checkpoint, 3 capstones, viva, live coding, checklist)");

    echo "== The exam\n";
    $exam = get_fast_modinfo(COURSE, $u->id)->get_cm($examcmid);
    say($exam->available, 'with every topic quiz attempted, the exam unlocks');
    if ($exam->available) {
        $t = microtime(true);
        [$n, $grade, $max] = attempt_quiz($exam, $u);
        \core\cron::setup_user();
        say($n == 60, "the exam drew $n questions (expected 60)");
        say(cm_state($exam, $u->id) == COMPLETION_COMPLETE_PASS, sprintf('exam passed: %.1f/%s (%.1fs)', $grade, $max, microtime(true) - $t));
        $cert = get_fast_modinfo(COURSE, $u->id)->get_cm(264);
        say(!$cert->available, 'the certificate stays locked until the human-graded work is passed');
    }
}

if ($cmd === 'grade') {
    $u = learner();
    echo "== Grading as a teacher (rubric totals must equal the recorded grade)\n";
    $plan = [
        260 => [[1], null], 261 => [[1], null], 262 => [[1], null], 263 => [[1], null],   // projects: all "Competent" = 10/15
        242 => [[2], null],                                    // Capstone 1: all level 3 of 4 = 15/20
        243 => [[2, 2, 2, 2, 1], null],                        // Capstone 2: 3+3+3+3+2 = 14/20, exactly the pass mark
        244 => [[2], null],                                    // Capstone 3: 15/20
        361 => [[1], null], 362 => [[1], null],                // viva and live coding: all "Competent" = 10/15
        360 => [null, 7.0],                                    // checkpoint: 7/10, exactly the pass mark
    ];
    foreach ($plan as $cmid => [$levels, $simple]) {
        $cm = get_coursemodule_from_id('assign', $cmid, 0, false, MUST_EXIST);
        [$method, $expected, $got, $pass, $max] = grade_assignment($cm, $u, $levels, $simple);
        \core\cron::setup_user();
        $state = cm_state($cm, $u->id);
        say(abs($got - $expected) < 0.01 && $state == COMPLETION_COMPLETE_PASS,
            sprintf('%-46s %s total %s -> grade %s/%s (pass %s), %s', mb_substr($cm->name, 0, 46), $method,
                $expected, 0 + $got, 0 + $max, 0 + $pass, $state == COMPLETION_COMPLETE_PASS ? 'passed' : "state $state"));
    }
    $checklist = get_coursemodule_from_id('assign', 259, 0, false, MUST_EXIST);
    say(cm_state($checklist, $u->id) == COMPLETION_COMPLETE, 'Getting Hired checklist is complete on submission');
}

if ($cmd === 'finish') {
    $u = learner();
    echo "== Certificate\n";
    $cert = get_fast_modinfo(COURSE, $u->id)->get_cm(264);
    say($cert->available, 'the certificate is now available: Capstone 1 + two others, exam, viva, live coding, checkpoint, projects, quizzes');
    if (!$cert->available) {
        echo "     restriction: " . strip_tags($cert->availableinfo) . "\n";
    }
    $certid = $cert->instance;
    $issue = $DB->get_record('customcert_issues', ['customcertid' => $certid, 'userid' => $u->id]);
    if (!$issue && $cert->available) {
        // The scheduled task that cron runs every minute issues certificates to everyone who meets the
        // conditions (and queues the emails).
        $task = new \mod_customcert\task\issue_certificates_task();
        $task->execute();
        $issue = $DB->get_record('customcert_issues', ['customcertid' => $certid, 'userid' => $u->id]);
    }
    say((bool) $issue, $issue ? "certificate issued by the scheduled task, code {$issue->code}" : 'certificate not issued');
    if ($issue) {
        (new completion_info(get_course(COURSE)))->set_module_viewed($cert, $u->id);
        echo "     verify URL: " . (new moodle_url('/mod/customcert/verify_certificate.php',
            ['contextid' => context_module::instance($cert->id)->id, 'code' => $issue->code]))->out(false) . "\n";
        $trec = $DB->get_record('customcert_templates', ['id' => $DB->get_field('customcert', 'templateid', ['id' => $certid])]);
        $pdf = new \mod_customcert\template((int) $trec->id, $trec->name, (int) $trec->contextid);
        $content = $pdf->generate_pdf(false, $u->id, true);
        say(str_starts_with($content, '%PDF'), sprintf('the certificate PDF renders (%d KB)', strlen($content) / 1024));
    }

    echo "== Course completion\n";
    $task = new \core\task\completion_regular_task();
    $task->execute();
    $cc = $DB->get_record('course_completions', ['course' => COURSE, 'userid' => $u->id]);
    say($cc && $cc->timecompleted, $cc && $cc->timecompleted ? 'Phase 1 is complete for the learner' : 'Phase 1 is not complete');
    if (!$cc || !$cc->timecompleted) {
        $info = new completion_info(get_course(COURSE));
        $missing = [];
        foreach ($info->get_completions($u->id) as $c) {
            if (!$c->is_complete()) {
                $missing[] = strip_tags($c->get_criteria()->get_title_detailed());
            }
        }
        echo "     incomplete criteria: " . implode('; ', array_slice($missing, 0, 10)) . "\n";
    }
}

if ($cmd === 'status') {
    foreach ([LEARNER, LEARNER2] as $name) {
        $u = $DB->get_record('user', ['username' => $name, 'deleted' => 0]);
        echo $name . ': ' . ($u ? "id {$u->id}" : 'absent') . "\n";
    }
}

if ($cmd === 'cleanup') {
    foreach ([LEARNER, LEARNER2] as $name) {
        $u = $DB->get_record('user', ['username' => $name, 'deleted' => 0]);
        if (!$u) {
            continue;
        }
        $manager = new \core_privacy\manager();
        $collection = new \core_privacy\local\request\contextlist_collection($u->id);
        foreach ($manager->get_contexts_for_userid($u->id) as $list) {
            $collection->add_contextlist(new \core_privacy\local\request\approved_contextlist(
                $u, $list->get_component(), $list->get_contextids()));
        }
        $manager->delete_data_for_user($collection);
        delete_user($u);
        $left = $DB->count_records('quiz_attempts', ['userid' => $u->id])
            + $DB->count_records('assign_submission', ['userid' => $u->id])
            + $DB->count_records('customcert_issues', ['userid' => $u->id])
            + $DB->count_records('grade_grades', ['userid' => $u->id]);
        say($left === 0, "$name deleted with all their data (records left: $left)");
    }
}

echo $failures ? "FAILURES: $failures\n" : "ALL OK\n";
