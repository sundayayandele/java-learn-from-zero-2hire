-- Drill 11 (week 14). Recursive CTE.
-- Everyone who reports to Tunde Bakare (employee 2), directly or indirectly, with their level below him
-- (direct reports are level 1). Columns: id, name, level. Order by level, then id.
-- Your query below:
WITH RECURSIVE reports AS (
    SELECT id, name, 1 AS level FROM employee WHERE manager_id = 2
    UNION ALL
    SELECT e.id, e.name, r.level + 1 FROM employee e JOIN reports r ON e.manager_id = r.id
)
SELECT id, name, level FROM reports ORDER BY level, id;
