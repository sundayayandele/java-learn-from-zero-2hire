-- Drill 07 (week 11). LAG.
-- Total transaction value per calendar month of 2026, the previous month's value, and the change.
-- Columns: month (first day, as a date), total, previous_total, change (NULL for the first month).
-- Order by month.
-- Your query below:
SELECT month, total,
       LAG(total) OVER (ORDER BY month) AS previous_total,
       total - LAG(total) OVER (ORDER BY month) AS change
FROM (SELECT DATE_TRUNC('month', booked_at)::date AS month, SUM(amount) AS total
      FROM txn GROUP BY 1) m
ORDER BY month;
