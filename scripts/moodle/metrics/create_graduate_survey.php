<?php
// Create the graduate survey (review item 19): a Feedback activity in Phase 2's Getting hired section that graduates
// answer 3 and 6 months after completing Phase 2. weekly_plan.php sends the invitations; funnel.php counts the jobs.
// Safe to re-run: it does nothing if the survey exists.
//   php create_graduate_survey.php          dry run
//   php create_graduate_survey.php apply
define('CLI_SCRIPT', true);
require('/var/www/html/config.php');
require_once($CFG->dirroot . '/course/modlib.php');
\core\cron::setup_user(get_admin());

const COURSE = 5;
const NAME = 'Graduate survey: 3 and 6 months after Phase 2';
$apply = ($argv[1] ?? '') === 'apply';
if ($DB->record_exists('feedback', ['course' => COURSE, 'name' => NAME])) { exit("survey exists\n"); }

$section = null;
foreach (get_fast_modinfo(COURSE)->get_section_info_all() as $s) {
    if (str_contains($s->name ?? '', 'Getting hired')) { $section = $s; }
}
$intro = '<p>Three and six months after you finish Phase 2 we ask how your job search is going. It takes two minutes. '
    . 'Your answers tell us whether the programme works and what to improve for the next cohort.</p>'
    . '<p><strong>How we use your answers:</strong> answering is voluntary. Your mentors and the ITLearning team see your answers '
    . 'with your name, so we can follow up if you want help. We publish only totals across a cohort, never individual answers or '
    . 'employer names, and keep your answers with your other course records under the <a href="/admin/tool/policy/viewall.php">privacy notice</a>.</p>';
say("+ feedback '" . NAME . "' in section {$section->section}, multiple submissions, not anonymous");
if (!$apply) { exit("dry run\n"); }

$mi = create_module((object)[
    'modulename' => 'feedback', 'module' => $DB->get_field('modules', 'id', ['name' => 'feedback']),
    'course' => COURSE, 'section' => $section->section, 'visible' => 1, 'name' => NAME,
    'introeditor' => ['text' => $intro, 'format' => FORMAT_HTML, 'itemid' => 0], 'showdescription' => 0,
    'anonymous' => 2, 'email_notification' => 0, 'multiple_submit' => 1, 'autonumbering' => 1,
    'page_after_submit' => '<p>Thank you. If you\'re still looking, reply to your mentor: we help graduates with CVs, mock interviews and introductions.</p>',
    'page_after_submitformat' => FORMAT_HTML,
    'site_after_submit' => '', 'publish_stats' => 0, 'timeopen' => 0, 'timeclose' => 0, 'completionsubmit' => 0,
    'completion' => 0, 'groupmode' => 0, 'groupingid' => 0, 'cmidnumber' => '',
]);
$items = [
    ['multichoice', 'when', 'Which survey is this?', 'r>>>>>3 months after finishing Phase 2|6 months after finishing Phase 2', 1],
    ['multichoice', 'situation', 'What is your situation now?', 'r>>>>>Employed as a software or backend engineer|Employed in another tech role (QA, data, support, DevOps)|Internship, graduate trainee or NYSC placement in tech|Freelance or contract developer work|Still looking for a tech role|Studying full time|Not looking for work at the moment', 1],
    ['textfield', 'title', 'If you have a role: what is your job title?', '40|120', 0],
    ['multichoice', 'employer', 'If you have a role: what kind of employer?', 'r>>>>>Bank|Fintech or payments company|Consultancy or outsourcing firm|Other technology company|Non-technology company|Not applicable', 0],
    ['multichoice', 'months', 'How long after finishing Phase 2 did you get your first offer?', 'r>>>>>Before finishing|0–1 months|2–3 months|4–6 months|More than 6 months|No offer yet', 1],
    ['multichoice', 'helped', 'How much did the programme help you get where you are?', 'r>>>>>A lot|Somewhat|A little|Not at all', 1],
    ['textarea', 'improve', 'What one thing should we change for the next cohort?', '60|5', 0],
];
foreach ($items as $i => [$typ, $label, $name, $presentation, $required]) {
    $DB->insert_record('feedback_item', ['feedback' => $mi->instance, 'template' => 0, 'name' => $name, 'label' => $label,
        'presentation' => $presentation, 'typ' => $typ, 'hasvalue' => 1, 'position' => $i + 1, 'required' => $required,
        'dependitem' => 0, 'dependvalue' => '', 'options' => $typ === 'multichoice' ? 'h' : '']);
}
rebuild_course_cache(COURSE, true);
say("created cm {$mi->coursemodule} with " . count($items) . " questions");

function say(string $m): void { echo $m, "\n"; }
