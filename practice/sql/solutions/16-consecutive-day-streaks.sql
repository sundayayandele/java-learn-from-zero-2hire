-- Drill 16 (week 15). Gaps and islands.
-- Customers who transacted (on any account) on at least 3 consecutive calendar days, with the length of their
-- longest streak. Columns: customer_id, longest_streak. Order by longest_streak descending, then customer_id.
-- Your query below:
WITH days AS (
    SELECT DISTINCT a.customer_id, t.booked_at::date AS d
    FROM txn t JOIN account a ON a.id = t.account_id
), islands AS (
    SELECT customer_id, d,
           d - (ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY d))::int AS grp
    FROM days
), streaks AS (
    SELECT customer_id, COUNT(*) AS len FROM islands GROUP BY customer_id, grp
)
SELECT customer_id, MAX(len) AS longest_streak
FROM streaks
GROUP BY customer_id
HAVING MAX(len) >= 3
ORDER BY longest_streak DESC, customer_id;
