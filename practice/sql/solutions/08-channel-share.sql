-- Drill 08 (week 11). Share of a total.
-- Each channel's share of total DEBIT value, as a percentage rounded to 2 decimal places.
-- Columns: channel, debit_value, pct. Order by pct descending, then channel.
-- Your query below:
SELECT channel, SUM(amount) AS debit_value,
       ROUND(100 * SUM(amount) / SUM(SUM(amount)) OVER (), 2) AS pct
FROM txn
WHERE direction = 'DEBIT'
GROUP BY channel
ORDER BY pct DESC, channel;
