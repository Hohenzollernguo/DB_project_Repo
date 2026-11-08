-- Q1～Q7：先运行 import.sql，再按题选择执行。
-- Q4/Q5 线路名、Q6 地标模式、Q7 参数可以按需修改。

-- Q1（A）：统计车站总数，预期为 439。
select count(*) as station_count from public.station;

-- Q2（B）：统计线路总数。
select count(distinct line_id) from public.metro_line;

-- Q3（A）：按行政区统计运营中的车站数。
select d.name_en as district, count(s.station_id) as station_count
from public.district d
left join public.station s on s.district_id = d.district_id
    and exists (select 1 from public.line_station ls
                where ls.station_id = s.station_id and ls.status = 'Operation')
group by d.district_id, d.name_en
order by d.name_en;

-- Q4（B）：按开通年份统计指定线路的车站数。
-- 将 'Line 1' 改成需要查询的线路英文名。
select opening_year, count(*) as cnt
from public.line_station
where line_id = (select line_id from public.metro_line where english_name = 'Line 1')
group by opening_year
order by opening_year;

-- Q5（A）：查询指定线路上出口最多的车站，并列时全部显示。
-- 将 'Line 1' 改成需要查询的线路英文名。
with exit_counts as (
    select s.station_id, s.english_name, count(e.exit_id) as exit_count
    from public.metro_line l
    join public.line_station ls on ls.line_id = l.line_id
    join public.station s on s.station_id = ls.station_id
    left join public.station_exit e on e.station_id = s.station_id
    where l.english_name = 'Line 1'
    group by s.station_id, s.english_name
)
select english_name, exit_count from exit_counts
where exit_count = (select max(exit_count) from exit_counts)
order by english_name, station_id;

-- Q6（A）：根据地标名称查询车站和出口，可修改匹配条件。
select s.chinese_name, d.name_en as district, e.exit_code, m.name as landmark
from public.landmark m
join public.exit_landmark em on em.landmark_id = m.landmark_id
join public.station_exit e on e.exit_id = em.exit_id
join public.station s on s.station_id = e.station_id
join public.district d on d.district_id = s.district_id
where m.name like '塘朗%'
order by s.english_name, d.name_en, s.chinese_name, e.exit_code, m.name;

-- Q7（B）：查询指定车站前后相隔 n 站的车站。
-- 先完整执行函数定义（到 $$;），再执行调用语句。
create or replace function public.get_station_n_neighbor(station_name text, line_name text, n integer)
    returns setof text
    language plpgsql
    set search_path = public, pg_temp
as
$$
declare
    prename  text;
    nextname text;
begin

    if n isnull or n < 0 then
        raise exception 'n无效' using errcode = '22023';
    end if;

    with t1 as (select station_id, position, english_name
                from (select station_id, position
                      from line_station
                      where line_id = (select line_id
                                       from metro_line
                                       where english_name = line_name)) as t
                         natural left join station),
         t2 as (select position, english_name
                from t1
                where english_name = station_name)
    select pre_name, after_name
    into strict prename,nextname
    from (
             t2 left join (select position, english_name as pre_name
                           from t1) as t3 on t2.position::bigint = t3.position::bigint + n left join (select position, english_name as after_name
                                                                                      from t1) as t4
             on t2.position::bigint = t4.position::bigint - n
             );
    return next prename;
    return next nextname;
    return;
exception
    when no_data_found then
        raise exception
            '线路 % 不存在，或站点 % 不在这条线路上',
            line_name, station_name using errcode = 'P0002';

    when too_many_rows then
        raise exception
            '线路 % 内有多个英文名为 % 的车站，无法唯一定位',
            line_name, station_name using errcode = 'P0003';
end;
$$;

-- 调用示例：Line 5 上 Mawan 前后相隔 3 站的车站。
select * from public.get_station_n_neighbor('Mawan', 'Line 5', 3);
