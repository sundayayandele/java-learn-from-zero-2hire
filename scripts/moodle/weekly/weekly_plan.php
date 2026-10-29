<?php
// Weekly plan messages for itlearning.tech (review item 11).
//
// Every Monday each active learner gets "this week's plan" for each course they're in, counted from their own
// enrolment date: the topics, the week's milestone, how far behind the plan they are, a nudge if they haven't
// opened the course for 7 days, and the office hour link once it's published. Mentors get a digest of learners
// who have been silent for 14 days or more (editing teachers see everyone; non-editing teachers see their groups).
//
// Runs inside the Moodle container (itl-weekly-plan.sh copies it in):
//   php weekly_plan.php --plan=/tmp/weekly-plan.json                 dry run: print what would be sent
//   php weekly_plan.php --plan=/tmp/weekly-plan.json --send          send, at most once per learner per week
//   options: --courses=4:1,5:2 (course id:phase)  --user=ID (one learner; the mentor digest is only printed)
//            --now=YYYY-MM-DD  --force (resend)
//
// The plan (lms/moodle/import/weekly-plan.json) is generated from curriculum.json by scripts/moodle/build_moodle.py.
// Messages use the core "Course content changes" notification, so learners control them in their preferences.
define('CLI_SCRIPT', true);
require('/var/www/html/config.php');
require_once($CFG->libdir . '/clilib.php');
require_once($CFG->libdir . '/completionlib.php');

[$opts] = cli_get_params(['plan' => '', 'send' => false, 'courses' => '4:1,5:2', 'user' => 0, 'now' => '', 'force' => false], []);
$plan = json_decode(file_get_contents($opts['plan']), true) ?: cli_error('Pass --plan=weekly-plan.json');
$now = $opts['now'] ? strtotime($opts['now'] . ' 06:00 UTC') : time();
const WEEK = 7 * 86400;
\core\cron::setup_user();

$sent = $skipped = 0;
foreach (explode(',', $opts['courses']) as $pair) {
    [$courseid, $phase] = array_map('intval', explode(':', $pair));
    $course = get_course($courseid);
    $p = $plan['phases'][(string)$phase] ?? cli_error("Phase $phase isn't in the plan");
    $weeks = array_column($p['weeks'], null, 'week');
    $first = max(0, (int)$p['first_week']);
    $last = (int)$p['last_week'];
    $start1 = $p['first_week'] == 0 ? 1 : $first;          // Phase 1's week 0 is the optional on-ramp
    $length = $last - $start1 + 1;
    $courseurl = (new moodle_url('/course/view.php', ['id' => $courseid]))->out(false);

    $modinfo = get_fast_modinfo($course);
    $pages = $quizzes = [];
    $officehour = $entrycheck = null;
    foreach ($modinfo->get_cms() as $cm) {
        if (preg_match('/^(\d+\.\d+) Lesson:/', $cm->name, $m)) { $pages[$m[1]] = $cm; }
        if (preg_match('/^(\d+\.\d+) Check your understanding/', $cm->name, $m)) { $quizzes[$m[1]] = $cm; }
        if ($cm->modname === 'url' && str_starts_with($cm->name, 'Weekly office hour') && $cm->visible) { $officehour = $cm; }
        if ($cm->modname === 'quiz' && str_starts_with($cm->name, 'Entry check')) { $entrycheck = $cm; }
    }
    $required = [];   // core topic id => programme week
    foreach ($p['weeks'] as $w) {
        foreach ($w['topics'] as $t) {
            if (!$t['optional']) { $required[$t['id']] = (int)$w['week']; }
        }
    }

    $completion = new completion_info($course);
    $studentrole = $DB->get_field('role', 'id', ['shortname' => 'student']);
    $learners = $DB->get_records_sql("
        SELECT u.*, la.timeaccess,
               (SELECT MIN(CASE WHEN ue.timestart > 0 THEN ue.timestart ELSE ue.timecreated END)
                  FROM {user_enrolments} ue JOIN {enrol} e ON e.id = ue.enrolid
                 WHERE ue.userid = u.id AND e.courseid = :c1 AND e.status = 0 AND ue.status = 0
                       AND (ue.timeend = 0 OR ue.timeend > :now1)) AS planstart
          FROM {user} u
          JOIN {role_assignments} ra ON ra.userid = u.id AND ra.roleid = :role AND ra.contextid = :ctx
     LEFT JOIN {user_lastaccess} la ON la.userid = u.id AND la.courseid = :c2
         WHERE u.deleted = 0 AND u.suspended = 0" . ($opts['user'] ? ' AND u.id = :uid' : ''),
        ['now1' => $now, 'c1' => $courseid, 'c2' => $courseid, 'role' => $studentrole,
         'ctx' => context_course::instance($courseid)->id, 'uid' => (int)$opts['user']]);

    $silent = [];   // userid => [user, days]
    foreach ($learners as $u) {
        if ($DB->record_exists_select('course_completions', 'userid = ? AND course = ? AND timecompleted > 0', [$u->id, $courseid])) {
            continue;
        }
        if (!$u->planstart || $u->planstart > $now) { continue; }
        $index = intdiv($now - $u->planstart, WEEK);
        $week = $first + $index;
        if ($week > $last + 1) { continue; }
        $daysaway = $u->timeaccess ? intdiv($now - $u->timeaccess, 86400) : intdiv($now - $u->planstart, 86400);
        if ($daysaway >= 14) { $silent[$u->id] = [$u, $daysaway, $week]; }

        $pref = "itl_weeklyplan_c$courseid";
        $stamp = "week $week";
        if (!$opts['force'] && get_user_preferences($pref, '', $u->id) === $stamp) { $skipped++; continue; }

        $behind = [];
        foreach ($required as $tid => $due) {
            if ($due < $week && isset($quizzes[$tid])
                    && $completion->get_data($quizzes[$tid], false, $u->id)->completionstate == COMPLETION_INCOMPLETE) {
                $behind[] = $tid;
            }
        }
        [$subject, $html] = compose($u, $course, $phase, $week, $index, $length, $start1, $last, $weeks[$week] ?? null,
            $pages, $behind, $daysaway, $officehour, $entrycheck, $courseurl);
        if (!$opts['send']) {
            echo "--- to {$u->id} " . fullname($u) . " (course $courseid, week $week): $subject\n" . html_to_text($html, 100) . "\n";
            continue;
        }
        notify($u, $courseid, $subject, $html, $courseurl, $course->fullname);
        set_user_preference($pref, $stamp, $u->id);
        $sent++;
    }

    // Mentor digest: learners silent for 14+ days.
    if ($silent) {
        $context = context_course::instance($courseid);
        $editing = get_role_users($DB->get_field('role', 'id', ['shortname' => 'editingteacher']), $context);
        $mentors = get_role_users($DB->get_field('role', 'id', ['shortname' => 'teacher']), $context);
        $recipients = [];
        foreach ($silent as $uid => $row) {
            foreach ($editing as $t) { $recipients[$t->id][$uid] = $row; }
            foreach ($mentors as $t) {
                if (array_intersect(array_keys(groups_get_user_groups($courseid, $t->id)[0] ?? []),
                        array_keys(groups_get_user_groups($courseid, $uid)[0] ?? []))) {
                    $recipients[$t->id][$uid] = $row;
                }
            }
        }
        foreach ($recipients as $tid => $rows) {
            $teacher = core_user::get_user($tid);
            $items = '';
            foreach ($rows as [$u, $days, $week]) {
                $profile = (new moodle_url('/user/view.php', ['id' => $u->id, 'course' => $courseid]))->out(false);
                $items .= '<li><a href="' . $profile . '">' . s(fullname($u)) . "</a>: no visit for $days days, plan week $week</li>";
            }
            $subject = count($rows) . " learner(s) silent for 2+ weeks in {$course->shortname}";
            $html = '<p>These learners haven\'t opened the course for 14 days or more:</p><ul>' . $items . '</ul>'
                . '<p>Send each a short, friendly private message (mentor guide, section 8). After 4 weeks the lead mentor decides whether to pause the enrolment.</p>';
            if (!$opts['send'] || $opts['user']) {   // a one-learner test run never messages staff
                echo "--- digest to {$teacher->id} " . fullname($teacher) . ": $subject\n" . html_to_text($html, 100) . "\n";
                continue;
            }
            $dkey = "itl_weeklyplan_digest_c$courseid";
            $dstamp = date('o-W', $now);
            if (!$opts['force'] && get_user_preferences($dkey, '', $tid) === $dstamp) { continue; }
            notify($teacher, $courseid, $subject, $html, $courseurl, $course->fullname);
            set_user_preference($dkey, $dstamp, $tid);
        }
    }
}
echo ($opts['send'] ? "sent $sent" : "dry run") . ", $skipped already had this week's plan\n";

function compose(stdClass $u, stdClass $course, int $phase, int $week, int $index, int $length, int $start1, int $last,
        ?array $plan, array $pages, array $behind, int $daysaway, ?cm_info $officehour, ?cm_info $entrycheck, string $courseurl): array {
    $name = s($u->firstname);
    $topics = $plan['topics'] ?? [];
    if ($week > $last) {
        $subject = "Phase $phase: your scheduled weeks are over";
        $html = "<p>Hi $name,</p><p>You've reached the end of the scheduled Phase $phase weeks. Many learners need a few more; "
            . 'that\'s fine. Keep going at a steady pace and tell your mentor your target date.</p>';
    } else {
        $label = $week >= $start1 ? 'week ' . ($week - $start1 + 1) . " of $length" : 'week 0 (the optional on-ramp)';
        $titles = implode(', ', array_map(fn($t) => $t['id'], array_filter($topics, fn($t) => !$t['optional'])));
        $subject = "Phase $phase, $label" . ($titles ? ": topics $titles" : '');
        $list = '';
        foreach ($topics as $t) {
            $link = isset($pages[$t['id']]) ? $pages[$t['id']]->url->out(false) : $courseurl;
            $list .= '<li><a href="' . $link . '">' . s($t['id'] . ' ' . $t['title']) . '</a>' . ($t['optional'] ? ' (optional)' : '') . '</li>';
        }
        $html = "<p>Hi $name,</p><p>This is $label of your Phase $phase plan (programme week $week). This week:</p><ul>$list</ul>";
        if ($week === 0 && $entrycheck) {
            $html .= '<p>Start with the <a href="' . $entrycheck->url->out(false) . '">entry check</a>: with 12 or more out of 15 '
                . 'you can skip the on-ramp and go straight to topic 1.1.</p>';
        }
        if (!empty($plan['milestone'])) { $html .= '<p><strong>Milestone:</strong> ' . s($plan['milestone']) . '</p>'; }
    }
    if ($behind) {
        $html .= '<p>You\'re ' . count($behind) . ' topic quiz' . (count($behind) === 1 ? '' : 'zes') . ' behind the plan ('
            . s(implode(', ', array_slice($behind, 0, 8))) . (count($behind) > 8 ? ', …' : '') . '). A little every day works '
            . 'better than one long weekend; if something is blocking you, ask in <em>Questions and help</em>.</p>';
    }
    if ($daysaway >= 7) {
        $html .= "<p>We haven't seen you in the course for $daysaway days. Even 30 minutes today keeps the habit going.</p>";
    }
    if ($officehour) {
        $html .= '<p><strong>Office hour:</strong> ' . strip_tags($officehour->get_formatted_content() ?: '', '<strong><em>')
            . ' <a href="' . $officehour->url->out(false) . '">Join</a></p>';
    }
    $html .= '<p><a href="' . $courseurl . '">Open ' . s($course->fullname) . '</a></p>';
    return [$subject, $html];
}

function notify(stdClass $to, int $courseid, string $subject, string $html, string $url, string $urlname): void {
    $m = new \core\message\message();
    $m->component = 'moodle';
    $m->name = 'coursecontentupdated';
    $m->userfrom = core_user::get_noreply_user();
    $m->userto = $to;
    $m->subject = $subject;
    $m->fullmessage = html_to_text($html);
    $m->fullmessageformat = FORMAT_PLAIN;
    $m->fullmessagehtml = $html;
    $m->smallmessage = $subject;
    $m->notification = 1;
    $m->contexturl = $url;
    $m->contexturlname = $urlname;
    $m->courseid = $courseid;
    message_send($m);
}
