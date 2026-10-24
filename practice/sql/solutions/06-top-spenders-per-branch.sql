-- Drill 06 (week 10). Top N per group.
-- For each branch, the top 3 customers by total DEBIT amount across all their accounts (ties share a rank,
-- so a branch may return more than 3 rows). Columns: branch_name, customer_id, total_debits, rnk.
-- Order by branch_name, rnk, customer_id.
-- Your query below:
SELECT branch_name, customer_id, total_debits, rnk
FROM (
    SELECT b.name AS branch_name, c.id AS customer_id, SUM(t.amount) AS total_debits,
           DENSE_RANK() OVER (PARTITION BY b.id ORDER BY SUM(t.amount) DESC) AS rnk
    FROM branch b
    JOIN customer c ON c.branch_id = b.id
    JOIN account a ON a.customer_id = c.id
    JOIN txn t ON t.account_id = a.id AND t.direction = 'DEBIT'
    GROUP BY b.id, b.name, c.id) ranked
WHERE rnk <= 3
ORDER BY branch_name, rnk, customer_id;
