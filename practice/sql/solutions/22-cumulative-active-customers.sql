-- Drill 22 (week 19). Cumulative distinct counts.
-- For each month of 2026, how many customers made their first-ever transaction that month, and the cumulative
-- number of customers who have transacted so far. Columns: month (date), new_customers, cumulative_customers.
-- Order by month.
-- Your query below:
SELECT month, new_customers, SUM(new_customers) OVER (ORDER BY month) AS cumulative_customers
FROM (SELECT first_month AS month, COUNT(*) AS new_customers
      FROM (SELECT a.customer_id, DATE_TRUNC('month', MIN(t.booked_at))::date AS first_month
            FROM txn t JOIN account a ON a.id = t.account_id
            GROUP BY a.customer_id) f
      GROUP BY first_month) m
ORDER BY month;
