-- Drill 18 (week 16). LAG over time.
-- For accounts with at least 10 transactions, the average gap in hours between consecutive transactions,
-- rounded to 1 decimal place. Columns: account_id, avg_gap_hours. Order by avg_gap_hours, then account_id.
-- Your query below:
SELECT account_id, ROUND(AVG(EXTRACT(EPOCH FROM gap) / 3600)::numeric, 1) AS avg_gap_hours
FROM (SELECT account_id, booked_at - LAG(booked_at) OVER (PARTITION BY account_id ORDER BY booked_at, id) AS gap
      FROM txn) g
WHERE gap IS NOT NULL
GROUP BY account_id
HAVING COUNT(*) >= 9
ORDER BY avg_gap_hours, account_id;
