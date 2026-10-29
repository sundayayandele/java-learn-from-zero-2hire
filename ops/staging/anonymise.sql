-- Staging copy of itlearning.tech: remove learners' personal data and secrets (review item 18).
-- Run against the staging database only (ops/staging/staging.sh does it). User 2, the site admin, is kept so the
-- operator can sign in with their usual password and authenticator; every other account becomes "Learner <id>".
UPDATE mdl_user
   SET firstname = 'Learner', lastname = CAST(id AS CHAR), username = CONCAT('learner', id),
       email = CONCAT('learner', id, '@staging.invalid'), password = '!', secret = '',
       phone1 = '', phone2 = '', address = '', city = '', institution = '', department = '', idnumber = '',
       description = NULL, lastip = '', imagealt = NULL, alternatename = NULL, middlename = NULL,
       firstnamephonetic = NULL, lastnamephonetic = NULL, picture = 0
 WHERE id > 2;
DELETE FROM mdl_tool_mfa WHERE userid > 2;
DELETE FROM mdl_auth_oauth2_linked_login;
DELETE FROM mdl_user_devices;
DELETE FROM mdl_user_private_key;
DELETE FROM mdl_external_tokens;
DELETE FROM mdl_sessions;
DELETE FROM mdl_logstore_standard_log;
DELETE FROM mdl_messages;
DELETE FROM mdl_notifications;
DELETE FROM mdl_feedback_value;
DELETE FROM mdl_feedback_completed;
UPDATE mdl_forum_posts SET subject = 'Post', message = '<p>(removed on staging)</p>' WHERE userid > 2;
UPDATE mdl_assignsubmission_onlinetext SET onlinetext = '<p>(removed on staging)</p>';
UPDATE mdl_assignfeedback_comments SET commenttext = '<p>(removed on staging)</p>';
SELECT COUNT(*) AS learners_anonymised FROM mdl_user WHERE id > 2 AND email LIKE '%@staging.invalid';
