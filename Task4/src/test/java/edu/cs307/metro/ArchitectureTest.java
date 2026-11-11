package edu.cs307.metro;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.config.DbConfig;
import edu.cs307.metro.model.*;
import edu.cs307.metro.ui.MetroUiPreview;
import java.awt.Component;
import java.awt.Container;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureTest {
    @Test
    void bootstrapAndInvalidRequestsDoNotOpenConnections() {
        var services = AppBootstrap.create(() -> { throw new AssertionError("Unexpected database access"); });
        AppException invalidStationSearch = assertThrows(AppException.class,
                () -> services.stations().search(null));
        assertEquals(ErrorCode.INVALID_INPUT, invalidStationSearch.code());
        AppException invalidStops = assertThrows(AppException.class,
                () -> services.lines().insertStops(1, 1, List.of()));
        assertEquals(ErrorCode.INVALID_INPUT, invalidStops.code());
        AppException invalidNeighbor = assertThrows(AppException.class,
                () -> services.neighbors().get("a", "b", -1));
        assertEquals(ErrorCode.INVALID_INPUT, invalidNeighbor.code());
        AppException invalidRoute = assertThrows(AppException.class, () -> services.routes().findRoute(0, 2));
        assertEquals(ErrorCode.INVALID_INPUT, invalidRoute.code());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(AppException.class,
                () -> services.landmarks().findRoute(" ", "b")).code());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(AppException.class,
                () -> services.landmarks().findRoute("a", null)).code());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(AppException.class,
                () -> services.landmarks().searchPrefix(null)).code());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(AppException.class,
                () -> services.fares().quote(0, 2)).code());
    }

    @Test
    void configuredLandmarkAndFareQueriesAccessDatabaseAndReportConnectionFailures() {
        AtomicInteger opened = new AtomicInteger();
        var services = AppBootstrap.create(() -> {
            opened.incrementAndGet();
            throw new SQLException("Controlled connection failure", "08006");
        });
        assertEquals(0, opened.get());
        assertEquals(ErrorCode.DB_ERROR, assertThrows(AppException.class,
                () -> services.landmarks().findRoute("a", "b")).code());
        assertEquals(ErrorCode.DB_ERROR, assertThrows(AppException.class,
                () -> services.fares().quote(1, 2)).code());
        assertEquals(2, opened.get());
    }

    @Test
    void configurationKeepsPasswordExactAndHidesItInDiagnostics() {
        DbConfig config = DbConfig.from(Map.of("DB_URL", "jdbc:postgresql://localhost:5432/metro_test",
                "DB_USER", " local ", "DB_PASSWORD", " secret "));
        assertEquals("local", config.user());
        assertEquals(" secret ", config.password());
        assertEquals("jdbc:postgresql://localhost:5432/metro_test", config.url());
        assertFalse(config.toString().contains("secret"));
        assertFalse(config.toString().contains("local"));
    }

    @Test
    void configurationRequiresExplicitDatabaseUrl() {
        AppException error = assertThrows(AppException.class,
                () -> DbConfig.from(Map.of("DB_USER", "user", "DB_PASSWORD", "secret")));
        assertEquals(ErrorCode.INVALID_INPUT, error.code());
    }

    @Test
    void incompleteConfigurationFailsClearlyWithoutPrintingValues() {
        AppException error = assertThrows(AppException.class,
                () -> DbConfig.from(Map.of("DB_USER", "user")));
        assertEquals(ErrorCode.INVALID_INPUT, error.code());
        assertThrows(AppException.class,
                () -> DbConfig.from(Map.of("DB_URL", "jdbc:other:", "DB_USER", "user", "DB_PASSWORD", "secret")));
    }

    @Test
    void routeResultsCannotBeChangedThroughCallerOwnedLists() {
        StationView station = new StationView(1, new StationKey("测试站", "Nanshan"), "Test", "Operation");
        List<StationView> stations = new ArrayList<>(List.of(station));
        RouteLeg leg = new RouteLeg(new LineView(1, "测试线", "Test Line"), stations);
        List<RouteLeg> legs = new ArrayList<>(List.of(leg));
        RouteResult route = new RouteResult(stations, legs, 0, 0);
        stations.clear();
        legs.clear();
        assertEquals(1, leg.stations().size());
        assertEquals(1, route.stations().size());
        assertEquals(1, route.legs().size());
        assertThrows(UnsupportedOperationException.class, () -> route.stations().clear());
    }

    @Test
    void neighborDtoKeepsBothNullableSides() {
        NeighborPair result = new NeighborPair(null, "Next");
        assertNull(result.lowerPositionName());
        assertEquals("Next", result.higherPositionName());
    }

    @Test
    void defaultCliHandlesBadInputThenExitsWithoutDatabase() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        int code = Main.run(new String[0], new ByteArrayInputStream("bad\n2\n0\n6\n\n\n0\n".getBytes(StandardCharsets.UTF_8)),
                output, output);
        assertEquals(0, code);
        String text = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("请输入 0 到 11"));
        assertTrue(text.contains("[INVALID_INPUT]"));
        assertTrue(text.contains("请输入起点地标全名："));
        assertTrue(text.contains("[INVALID_INPUT] 请输入起点地标全名。"));
        assertFalse(text.contains("尚未实现"));
    }

    @Test
    void mainWritesUtf8EvenWhenSystemStreamUsesAnotherEncoding() {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, StandardCharsets.US_ASCII));
            Main.main(new String[]{"--help"});
        } finally {
            System.setOut(original);
        }
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("用法"));
    }

    @Test
    void helpAndInvalidArgumentReturnUsefulExitCodes() {
        PrintStream sink = new PrintStream(OutputStream.nullOutputStream());
        assertEquals(0, Main.run(new String[]{"--help"}, InputStream.nullInputStream(), sink, sink));
        assertEquals(2, Main.run(new String[]{"--typo"}, InputStream.nullInputStream(), sink, sink));
        assertEquals(2, Main.run(new String[]{"--cli", "--gui"}, InputStream.nullInputStream(), sink, sink));
    }

    @Test
    void swingPanelBuildsOnEdtWithoutDatabaseAccess() throws Exception {
        var services = AppBootstrap.create(() -> { throw new AssertionError("Unexpected database access"); });
        var createContent = MetroUiPreview.class.getDeclaredMethod("createContent",
                edu.cs307.metro.bootstrap.AppServices.class);
        createContent.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                JPanel panel = (JPanel) createContent.invoke(null, services);
                Set<String> buttons = buttonLabels(panel);
                assertTrue(buttons.containsAll(List.of("路线查询", "车站管理", "线路管理", "站线管理",
                        "新增车站", "新增线路", "插入车站")), buttons.toString());
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Failed to build the current GUI", failure);
            }
        });
    }

    private static Set<String> buttonLabels(Container container) {
        Set<String> labels = new HashSet<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button) labels.add(button.getText());
            if (component instanceof Container child) labels.addAll(buttonLabels(child));
        }
        return labels;
    }

    @Test
    void postgresqlDriverIsAvailableAtRuntimeWithoutOpeningAConnection() throws Exception {
        assertTrue(DriverManager.getDriver("jdbc:postgresql://localhost/metro_test")
                .acceptsURL("jdbc:postgresql://localhost/metro_test"));
    }
}
