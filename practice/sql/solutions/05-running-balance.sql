-- Drill 05 (week 11). Window function: running total.
-- For account 1, list every transaction in time order with a running balance starting from 0
-- (credits add, debits subtract). Columns: id, booked_at, signed_amount, running_balance.
-- Order by booked_at, then id.
-- Your query below:
SELECT id, booked_at,
       CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END AS signed_amount,
       SUM(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END)
           OVER (ORDER BY booked_at, id) AS running_balance
FROM txn
WHERE account_id = 1
ORDER BY booked_at, id;
