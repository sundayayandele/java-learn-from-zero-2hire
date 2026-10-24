-- Drill 15 (week 15). Calendar buckets with zero-filled gaps.
-- Number of transactions on account 9 in every ISO week (weeks start on Monday) from the week of 2026-03-02 to
-- the week of 2026-10-26 inclusive, showing 0 for weeks without any.
-- Columns: week_start (date), txn_count. Order by week_start.
-- Your query below:
SELECT w::date AS week_start, COUNT(t.id) AS txn_count
FROM GENERATE_SERIES(DATE '2026-03-02', DATE '2026-10-26', INTERVAL '7 days') AS w
LEFT JOIN txn t ON t.account_id = 9 AND DATE_TRUNC('week', t.booked_at) = w
GROUP BY w
ORDER BY w;
