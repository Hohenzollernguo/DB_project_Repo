"""验证预计算 JSON，使用站点 BFS + 最短路分层动态规划独立核对最优代价。"""

import argparse
import collections
import csv
import json
from pathlib import Path


def require(condition, message):
    if not condition:
        raise ValueError(message)


def station(value):
    require(set(value) == {"name", "district"}, "站点字段不符合约定")
    return value["name"], value["district"]


def load_network(path):
    lines = collections.defaultdict(list)
    stations = set()
    with path.open(encoding="utf-8-sig", newline="") as source:
        for row in csv.DictReader(source):
            row = {key: value.strip() for key, value in row.items()}
            key = row["Station Name"], row["District"]
            stations.add(key)
            lines[row["Line Chineses"]].append((int(row["Num"]), key, row["Status"]))
    neighbors = {key: [] for key in stations}
    allowed_edges = set()
    for line, stops in lines.items():
        stops.sort()
        require([stop[0] for stop in stops] == list(range(1, len(stops) + 1)), "原始站序不连续")
        for previous, current in zip(stops, stops[1:]):
            if previous[2] == current[2] == "Operation":
                a, b = previous[1], current[1]
                neighbors[a].append((b, line))
                neighbors[b].append((a, line))
                allowed_edges.update(((a, b, line), (b, a, line)))
    return stations, neighbors, allowed_edges


def optimal_costs(source, neighbors):
    # 先在物理站点图上计算最少段数。
    distance = {source: 0}
    queue = collections.deque([source])
    order = []
    while queue:
        current = queue.popleft()
        order.append(current)
        for destination, _ in neighbors[current]:
            if destination not in distance:
                distance[destination] = distance[current] + 1
                queue.append(destination)
    # 最短路线的每一步必须进入下一层，在这个 DAG 上比较最少换乘。
    transfers = {key: {} for key in distance}
    transfers[source][None] = 0
    for current in order:
        for destination, line in neighbors[current]:
            if distance[destination] != distance[current] + 1:
                continue
            candidate = min(cost + int(incoming is not None and incoming != line)
                            for incoming, cost in transfers[current].items())
            previous = transfers[destination].get(line)
            if previous is None or candidate < previous:
                transfers[destination][line] = candidate
    return {key: (distance[key], min(transfers[key].values())) for key in distance}


def records(path):
    # 生成器每条结果占一行。逐行解析可验证大文件且不加载全部路径对象。
    with path.open(encoding="utf-8") as cache:
        prefix = "".join(next(cache) for _ in range(4))
        header = json.loads(prefix + "]}")
        require(header["formatVersion"] == 1, "缓存版本不受支持")
        require(bool(header["routingRule"]), "缺少路径选择规则")
        previous_had_comma = True
        count = 0
        for line in cache:
            text = line.strip()
            if text == "]":
                require(count == 0 or not previous_had_comma, "routes 存在末尾多余逗号")
                require(cache.read().strip() == "}", "JSON 尾部结构不正确")
                return
            require(previous_had_comma, "相邻路线之间缺少逗号")
            previous_had_comma = text.endswith(",")
            if previous_had_comma:
                text = text[:-1]
            yield json.loads(text)
            count += 1
        raise ValueError("JSON 文件未完整结束")


def verify(csv_path, cache_path):
    stations, neighbors, allowed_edges = load_network(csv_path)
    seen = set()
    reachable = 0
    all_costs = {source: optimal_costs(source, neighbors) for source in stations}
    expected_count = sum(len(costs) - 1 for costs in all_costs.values())
    max_stops = 0
    max_transfers = 0
    example = None
    for route in records(cache_path):
        source, destination = station(route["from"]), station(route["to"])
        require(source in stations and destination in stations and source != destination, "起终点无效")
        pair = source, destination
        require(pair not in seen, "站点组合重复")
        seen.add(pair)
        expected = all_costs[source]
        require(destination in expected, "缓存包含不可达组合")
        require("status" not in route, "可达路线意外包含失败状态")
        require((route["stopCount"], route["transferCount"]) == expected[destination], "路线代价不是最优")
        previous_end = source
        previous_line = None
        stops = 0
        for leg in route["legs"]:
            keys = [station(value) for value in leg["stations"]]
            require(len(keys) >= 2 and keys[0] == previous_end, "路段不连续或包含空乘车段")
            require(leg["line"] != previous_line, "同一线路的连续路段未合并")
            for a, b in zip(keys, keys[1:]):
                require((a, b, leg["line"]) in allowed_edges, "路线包含非运营连接或跨站跳跃")
            previous_end = keys[-1]
            previous_line = leg["line"]
            stops += len(keys) - 1
        require(previous_end == destination, "路线终点不正确")
        require(stops == route["stopCount"], "路段站数与统计不一致")
        require(len(route["legs"]) - 1 == route["transferCount"], "换乘统计不一致")
        max_stops = max(max_stops, stops)
        max_transfers = max(max_transfers, route["transferCount"])
        if example is None and source[0] == "罗湖" and destination[0] == "机场":
            example = route
        reachable += 1
    require(len(seen) == expected_count, "未覆盖全部可达有向站点组合")
    unreachable = len(stations) * (len(stations) - 1) - expected_count
    print(json.dumps({"checked": len(seen), "reachable": reachable, "unreachable": unreachable,
                      "maxStopCount": max_stops, "maxTransferCount": max_transfers,
                      "bytes": cache_path.stat().st_size, "example": example}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("json", type=Path)
    args = parser.parse_args()
    verify(args.csv, args.json)
