-- A: Q1 / Q3 / Q5 / Q6. Execute individual queries in DataGrip.
-- Q1: expected 439.
SELECT COUNT(*) AS station_count FROM public.station;

-- Q3: count each operating station once per district, including districts with zero.
SELECT d.name_en AS district, COUNT(s.station_id) AS station_count
FROM public.district d
LEFT JOIN public.station s ON s.district_id = d.district_id
    AND EXISTS (SELECT 1 FROM public.line_station ls
                WHERE ls.station_id = s.station_id AND ls.status = 'Operation')
GROUP BY d.district_id, d.name_en
ORDER BY d.name_en;

-- Q5: change 'Line 1' to the requested English line name; preserve ties.
WITH exit_counts AS (
    SELECT s.station_id, s.english_name, COUNT(e.exit_id) AS exit_count
    FROM public.metro_line l
    JOIN public.line_station ls ON ls.line_id = l.line_id
    JOIN public.station s ON s.station_id = ls.station_id
    LEFT JOIN public.station_exit e ON e.station_id = s.station_id
    WHERE l.english_name = 'Line 1'
    GROUP BY s.station_id, s.english_name
)
SELECT english_name, exit_count FROM exit_counts
WHERE exit_count = (SELECT MAX(exit_count) FROM exit_counts)
ORDER BY english_name, station_id;

-- Q6: change the LIKE pattern as requested, e.g. '塘朗%'.
SELECT s.chinese_name, d.name_en AS district, e.exit_code, m.name AS landmark
FROM public.landmark m
JOIN public.exit_landmark em ON em.landmark_id = m.landmark_id
JOIN public.station_exit e ON e.exit_id = em.exit_id
JOIN public.station s ON s.station_id = e.station_id
JOIN public.district d ON d.district_id = s.district_id
WHERE m.name LIKE '塘朗%'
ORDER BY s.english_name, d.name_en, s.chinese_name, e.exit_code, m.name;
