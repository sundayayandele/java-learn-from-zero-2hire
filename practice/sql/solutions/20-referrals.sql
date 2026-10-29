-- Drill 20 (week 18). Self-join.
-- How many customers each referrer brought in, with the referrer's name; only referrers with at least one.
-- Columns: referrer_id, referrer_name, referrals. Order by referrals descending, then referrer_id.
-- Your query below:
SELECT r.id AS referrer_id, r.full_name AS referrer_name, COUNT(*) AS referrals
FROM customer c JOIN customer r ON r.id = c.referred_by
GROUP BY r.id, r.full_name
ORDER BY referrals DESC, r.id;
