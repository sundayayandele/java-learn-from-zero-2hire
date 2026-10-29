-- Drill 09 (week 13). Percentiles.
-- Median transaction amount per channel (all directions), rounded to 2 decimal places.
-- Columns: channel, median_amount. Order by channel.
-- Your query below:
SELECT channel, ROUND(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY amount)::numeric, 2) AS median_amount
FROM txn
GROUP BY channel
ORDER BY channel;
