-- Drill 02 (week 9). GROUP BY with HAVING.
-- List every transaction reference used more than once, with how many times it appears.
-- Columns: reference, occurrences. Order by occurrences descending, then reference.
-- Your query below:
SELECT reference, COUNT(*) AS occurrences
FROM txn
GROUP BY reference
HAVING COUNT(*) > 1
ORDER BY occurrences DESC, reference;
