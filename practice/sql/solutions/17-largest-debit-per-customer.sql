-- Drill 17 (week 17). Group maximum with ties.
-- Each customer's largest single DEBIT and every transaction that equals it (ties included).
-- Columns: customer_id, txn_id, amount. Order by customer_id, txn_id.
-- Your query below:
SELECT customer_id, txn_id, amount
FROM (SELECT a.customer_id, t.id AS txn_id, t.amount,
             RANK() OVER (PARTITION BY a.customer_id ORDER BY t.amount DESC) AS rnk
      FROM txn t JOIN account a ON a.id = t.account_id
      WHERE t.direction = 'DEBIT') x
WHERE rnk = 1
ORDER BY customer_id, txn_id;
