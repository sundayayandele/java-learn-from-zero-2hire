-- Drill 14 (week 15). Deduplication.
-- For each reference that appears more than once, keep only its latest row (highest booked_at, then highest id).
-- Columns: reference, txn_id, booked_at, account_id. Order by reference.
-- Your query below:
SELECT DISTINCT ON (reference) reference, id AS txn_id, booked_at, account_id
FROM txn
WHERE reference IN (SELECT reference FROM txn GROUP BY reference HAVING COUNT(*) > 1)
ORDER BY reference, booked_at DESC, id DESC;
