-- Drill 04 (week 10). Conditional aggregation.
-- For October 2026, give each account that had transactions its total credits and total debits.
-- Columns: account_id, credits, debits (0 when none). Order by account_id.
-- Your query below:
SELECT account_id,
       COALESCE(SUM(amount) FILTER (WHERE direction = 'CREDIT'), 0) AS credits,
       COALESCE(SUM(amount) FILTER (WHERE direction = 'DEBIT'), 0) AS debits
FROM txn
WHERE booked_at >= '2026-10-01' AND booked_at < '2026-11-01'
GROUP BY account_id
ORDER BY account_id;
