-- Drill 12 (week 13). Last activity and dates.
-- ACTIVE accounts that have transactions but none in the 90 days up to and including 2026-10-31 (that is,
-- the last transaction is before 2026-08-02). Columns: account_id, last_activity (a timestamp).
-- Order by account_id.
-- Your query below:
SELECT a.id AS account_id, MAX(t.booked_at) AS last_activity
FROM account a JOIN txn t ON t.account_id = a.id
WHERE a.status = 'ACTIVE'
GROUP BY a.id
HAVING MAX(t.booked_at) < DATE '2026-08-02'
ORDER BY a.id;
