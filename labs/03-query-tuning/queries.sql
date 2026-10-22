-- Five slow queries. For each: EXPLAIN (ANALYZE, BUFFERS), explain why it is slow, fix it, measure again.
-- Replace :acct with a real account id, e.g. \set acct 4242

-- Q1. Statement page: the last 20 transactions for one account.
SELECT posted_at, reference, direction, amount, narration
FROM txn
WHERE account_id = :acct
ORDER BY posted_at DESC
LIMIT 20;

-- Q2. Dormant-accounts report: active accounts with no customer-initiated transaction in 180 days.
SELECT a.number
FROM account a
WHERE a.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM txn t
      WHERE t.account_id = a.id
        AND t.channel <> 'SYSTEM'
        AND date(t.posted_at) > current_date - 180);

-- Q3. Monthly spend per merchant category for last month.
SELECT m.mcc, count(*) AS txns, sum(t.amount) AS total
FROM txn t JOIN merchant m ON m.id = t.merchant_id
WHERE t.direction = 'D'
  AND to_char(t.posted_at, 'YYYY-MM') = to_char(now() - interval '1 month', 'YYYY-MM')
GROUP BY m.mcc
ORDER BY total DESC;

-- Q4. Customer support search: transactions whose narration contains a fragment.
SELECT id, account_id, narration, amount
FROM txn
WHERE narration LIKE '%AIRTIME 1234%'
LIMIT 50;

-- Q5. Reconciliation: transactions of Lagos customers in the last 7 days, with their account numbers.
SELECT c.full_name, a.number, t.reference, t.amount
FROM customer c
JOIN account a ON a.customer_id = c.id
JOIN txn t ON t.account_id = a.id
WHERE c.state = 'Lagos'
  AND t.posted_at >= now() - interval '7 days';
