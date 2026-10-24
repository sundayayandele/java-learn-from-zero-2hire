-- Drill 19 (week 17). Consistency check.
-- Accounts whose stored balance differs from credits minus debits of their transactions (accounts without
-- transactions should compute to 0). Columns: account_id, stored, computed, difference (stored - computed).
-- Order by account_id.
-- Your query below:
SELECT a.id AS account_id, a.balance AS stored, COALESCE(s.net, 0) AS computed,
       a.balance - COALESCE(s.net, 0) AS difference
FROM account a
LEFT JOIN (SELECT account_id, SUM(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END) AS net
           FROM txn GROUP BY account_id) s ON s.account_id = a.id
WHERE a.balance <> COALESCE(s.net, 0)
ORDER BY a.id;
