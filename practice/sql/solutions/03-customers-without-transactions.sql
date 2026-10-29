-- Drill 03 (week 10). Anti-join.
-- List customers who have no transactions on any of their accounts.
-- Columns: id, full_name. Order by id.
-- Your query below:
SELECT c.id, c.full_name
FROM customer c
WHERE NOT EXISTS (
    SELECT 1 FROM account a JOIN txn t ON t.account_id = a.id WHERE a.customer_id = c.id)
ORDER BY c.id;
