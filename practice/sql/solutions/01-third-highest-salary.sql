-- Drill 01 (week 9). Ranking with ties.
-- Return the third-highest DISTINCT salary in the employee table as one column: salary.
-- If several employees share a salary, it counts once. (Classic interview question.)
-- Your query below:
SELECT DISTINCT salary
FROM (SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk FROM employee) ranked
WHERE rnk = 3;
