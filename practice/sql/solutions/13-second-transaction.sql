-- Drill 13 (week 14). ROW_NUMBER.
-- The second transaction (by booked_at, then id) of every account that has at least two.
-- Columns: account_id, txn_id, booked_at. Order by account_id.
-- Your query below:
SELECT account_id, id AS txn_id, booked_at
FROM (SELECT account_id, id, booked_at,
             ROW_NUMBER() OVER (PARTITION BY account_id ORDER BY booked_at, id) AS rn
      FROM txn) x
WHERE rn = 2
ORDER BY account_id;
