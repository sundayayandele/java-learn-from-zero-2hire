-- Drill 23 (week 20). Range windows (AML).
-- Flag credits between 900,000.00 and 999,999.99 inclusive where the same account received at least 3 such
-- credits within the 7 days ending at that credit (the credit itself and the 6 days before it).
-- Columns: account_id, txn_id, amount, credits_in_window. Order by account_id, txn_id.
-- Your query below:
SELECT account_id, txn_id, amount, credits_in_window
FROM (SELECT account_id, id AS txn_id, amount,
             COUNT(*) OVER (PARTITION BY account_id ORDER BY booked_at
                            RANGE BETWEEN INTERVAL '6 days' PRECEDING AND CURRENT ROW) AS credits_in_window
      FROM txn
      WHERE direction = 'CREDIT' AND amount BETWEEN 900000.00 AND 999999.99) x
WHERE credits_in_window >= 3
ORDER BY account_id, txn_id;
