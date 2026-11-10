package edu.cs307.metro.routing;

import edu.cs307.metro.model.StationKey;
import java.io.IOException;
import java.io.Writer;

/** 流式写出约定格式，避免同时在内存中保留全部路线和完整 JSON 文本。 */
final class RouteJsonWriter {
    private RouteJsonWriter() {}

    static void route(Writer writer, RoutePlanner.Route route) throws IOException {
        if (!route.reachable()) {
            throw new IllegalArgumentException("JSON 缓存只保存可达的最优路线");
        }
        writer.write("    {\"from\":");
        station(writer, route.from());
        writer.write(",\"to\":");
        station(writer, route.to());
        writer.write(",\"stopCount\":" + route.stopCount());
        writer.write(",\"transferCount\":" + route.transferCount());
        writer.write(",\"legs\":[");
        for (int i = 0; i < route.legs().size(); i++) {
            if (i > 0) writer.write(',');
            RoutePlanner.Leg leg = route.legs().get(i);
            writer.write("{\"line\":");
            string(writer, leg.line());
            writer.write(",\"stations\":[");
            for (int j = 0; j < leg.stations().size(); j++) {
                if (j > 0) writer.write(',');
                station(writer, leg.stations().get(j));
            }
            writer.write("]}");
        }
        writer.write("]}");
    }

    private static void station(Writer writer, StationKey station) throws IOException {
        writer.write("{\"name\":");
        string(writer, station.chineseName());
        writer.write(",\"district\":");
        string(writer, station.districtName());
        writer.write('}');
    }

    static void string(Writer writer, String value) throws IOException {
        writer.write('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> writer.write("\\\"");
                case '\\' -> writer.write("\\\\");
                case '\n' -> writer.write("\\n");
                case '\r' -> writer.write("\\r");
                case '\t' -> writer.write("\\t");
                default -> {
                    if (character < 0x20) writer.write(String.format("\\u%04x", (int) character));
                    else writer.write(character);
                }
            }
        }
        writer.write('"');
    }
}
