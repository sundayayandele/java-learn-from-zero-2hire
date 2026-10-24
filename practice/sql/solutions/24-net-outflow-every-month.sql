-- Drill 24 (week 19). Aggregates over aggregates (bool_and).
-- Customers whose debits exceeded their credits in every month they had any transactions, and how many such
-- months. Columns: customer_id, active_months. Order by customer_id.
-- Your query below:
SELECT customer_id, COUNT(*) AS active_months
FROM (SELECT a.customer_id, DATE_TRUNC('month', t.booked_at) AS month,
             SUM(CASE t.direction WHEN 'DEBIT' THEN t.amount ELSE -t.amount END) AS net_outflow
      FROM txn t JOIN account a ON a.id = t.account_id
      GROUP BY a.customer_id, DATE_TRUNC('month', t.booked_at)) m
GROUP BY customer_id
HAVING BOOL_AND(net_outflow > 0)
ORDER BY customer_id;
