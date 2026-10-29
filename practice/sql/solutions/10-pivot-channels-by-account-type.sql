-- Drill 10 (week 13). Pivot with FILTER.
-- Count transactions per account type, one column per channel.
-- Columns: type, pos, nip, ussd, atm, web. Order by type.
-- Your query below:
SELECT a.type,
       COUNT(*) FILTER (WHERE t.channel = 'POS')  AS pos,
       COUNT(*) FILTER (WHERE t.channel = 'NIP')  AS nip,
       COUNT(*) FILTER (WHERE t.channel = 'USSD') AS ussd,
       COUNT(*) FILTER (WHERE t.channel = 'ATM')  AS atm,
       COUNT(*) FILTER (WHERE t.channel = 'WEB')  AS web
FROM txn t JOIN account a ON a.id = t.account_id
GROUP BY a.type
ORDER BY a.type;
