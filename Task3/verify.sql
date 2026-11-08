-- Read-only checks after import.sql. Expected counts are in the third column.
SELECT 'district' AS table_name, COUNT(*) AS actual, 10 AS expected FROM public.district
UNION ALL SELECT 'station', COUNT(*), 439 FROM public.station
UNION ALL SELECT 'metro_line', COUNT(*), 29 FROM public.metro_line
UNION ALL SELECT 'line_station', COUNT(*), 567 FROM public.line_station
UNION ALL SELECT 'station_exit', COUNT(*), 1276 FROM public.station_exit
UNION ALL SELECT 'landmark', COUNT(*), 2872 FROM public.landmark
UNION ALL SELECT 'exit_landmark', COUNT(*), 3203 FROM public.exit_landmark;

-- Expected: zero rows.
SELECT line_id FROM public.line_station
GROUP BY line_id
HAVING MIN(position) <> 1 OR MAX(position) <> COUNT(*)
    OR COUNT(DISTINCT position) <> COUNT(*);

-- Both Liyuan stations remain distinct; exits are assigned to Longgang.
SELECT s.chinese_name, d.name_en, COUNT(e.exit_id) AS exits
FROM public.station s JOIN public.district d USING (district_id)
LEFT JOIN public.station_exit e USING (station_id)
WHERE s.chinese_name = '梨园'
GROUP BY s.station_id, s.chinese_name, d.name_en ORDER BY d.name_en;
