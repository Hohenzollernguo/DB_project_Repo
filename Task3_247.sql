/*
 Q2
 */
select count(distinct line_id)
from metro_line;

/*
 Q4
 */
select opening_year, count(*) cnt
from line_station
where line_id = (select line_id from metro_line where english_name=${line_name})
group by opening_year
order by opening_year;

/*
 Q7
 */
CREATE OR REPLACE function get_station_n_neighbor(station_name text, line_name text, n integer)
    returns setof text
    language plpgsql
as
$$
declare
    prename  text;
    nextname text;
begin

    if n isnull or n < 0 then
        raise exception 'n无效';
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
                           from t1) as t3 on t2.position = t3.position + n left join (select position, english_name as after_name
                                                                                      from t1) as t4
             on t2.position = t4.position - n
             );
    return next prename;
    return next nextname;
    return;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RAISE EXCEPTION
            '线路 % 不存在，或站点 % 不在这条线路上',
            line_name, station_name;

    WHEN TOO_MANY_ROWS THEN
        RAISE EXCEPTION
            '线路 % 内有多个英文名为 % 的车站，无法唯一定位',
            line_name, station_name;
end;
$$;

select * from get_station_n_neighbor('Mawan','Line 5',3)



