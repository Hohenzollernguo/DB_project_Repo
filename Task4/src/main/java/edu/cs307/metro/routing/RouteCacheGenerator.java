package edu.cs307.metro.routing;

import edu.cs307.metro.model.StationKey;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class RouteCacheGenerator {
    public static final String ROUTING_RULE = "最少站间段，其次最少换乘，"
            + "再按状态序列（中文站名、行政区、完整中文线路名）的字典序";

    public record Summary(int stationCount, int lineCount, long routeCount,
                          long reachableCount, long unreachableCount, long bytes) {}

    private RouteCacheGenerator() {}

    /** 检查全部不同站点的有向组合，仅写入可达组合的一条最优路线。 */
    public static Summary generate(Path source, Path output, PrintStream progress) throws IOException {
        Path inputPath = source.toAbsolutePath().normalize();
        Path outputPath = output.toAbsolutePath().normalize();
        if (inputPath.equals(outputPath) || (Files.exists(outputPath) && Files.isSameFile(inputPath, outputPath))) {
            throw new IllegalArgumentException("输出文件不能覆盖输入 CSV");
        }
        RouteNetwork network = CsvTopologyLoader.load(inputPath);
        RoutePlanner planner = new RoutePlanner(network);
        Files.createDirectories(outputPath.getParent());
        Path temporary = Files.createTempFile(outputPath.getParent(), "routes-", ".tmp");
        long total = 0;
        long reachable = 0;
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write("{\n  \"formatVersion\": 1,\n  \"routingRule\": ");
                RouteJsonWriter.string(writer, ROUTING_RULE);
                writer.write(",\n  \"routes\": [\n");
                int completed = 0;
                for (StationKey from : network.stations()) {
                    for (RoutePlanner.Route route : planner.from(from).values()) {
                        if (route.from().equals(route.to())) continue;
                        total++;
                        if (!route.reachable()) continue;
                        if (reachable > 0) writer.write(",\n");
                        RouteJsonWriter.route(writer, route);
                        reachable++;
                    }
                    completed++;
                    if (completed % 50 == 0 || completed == network.stations().size()) {
                        progress.printf("已计算起点 %d/%d%n", completed, network.stations().size());
                    }
                }
                writer.write("\n  ]\n}\n");
            }
            // 全部计算及写入成功后才替换目标文件，失败时保留原缓存。
            try {
                Files.move(temporary, outputPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, outputPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new Summary(network.stations().size(), network.lineCount(), total,
                reachable, total - reachable, Files.size(outputPath));
    }
}
