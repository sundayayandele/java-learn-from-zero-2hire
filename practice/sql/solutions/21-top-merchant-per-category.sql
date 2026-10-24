-- Drill 21 (week 18). Top 1 per group with a deterministic tie-break.
-- For each category, the merchant with the highest total DEBIT spend (on a tie, the alphabetically first).
-- Columns: category, merchant, spend. Order by category.
-- Your query below:
SELECT category, merchant, spend
FROM (SELECT category, merchant, SUM(amount) AS spend,
             ROW_NUMBER() OVER (PARTITION BY category ORDER BY SUM(amount) DESC, merchant) AS rn
      FROM txn
      WHERE direction = 'DEBIT' AND category IS NOT NULL
      GROUP BY category, merchant) x
WHERE rn = 1
ORDER BY category;
