package edu.cs307.metro;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.config.DbConfig;
import edu.cs307.metro.model.*;
import edu.cs307.metro.ui.DesktopApp;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureTest {
    @Test
    void bootstrapAndPendingBusinessDoNotOpenConnectionsOrPretendSuccess() {
        var services = AppBootstrap.create(() -> { throw new AssertionError("Unexpected database access"); });
        assertThrows(UnsupportedOperationException.class,
                () -> services.lines().update(1, new LineDraft("测试", "Test")));
        assertThrows(UnsupportedOperationException.class, () -> services.stations().search(""));
        assertThrows(UnsupportedOperationException.class, () -> services.lines().insertStops(1, 1, List.of()));
        assertThrows(UnsupportedOperationException.class, () -> services.neighbors().get("a", "b", 1));
        assertThrows(UnsupportedOperationException.class, () -> services.routes().findRoute(1, 2));
        assertThrows(UnsupportedOperationException.class, () -> services.landmarks().findRoute("a", "b"));
        FareQuote quote = services.fares().quote(1, 2);
        assertEquals("RULE_NOT_CONFIGURED", quote.status());
        assertNull(quote.amount());
        assertNull(quote.ruleVersion());
        assertEquals("CNY", quote.currency());
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
        int code = Main.run(new String[0], new ByteArrayInputStream("bad\n2\n0\n".getBytes(StandardCharsets.UTF_8)),
                output, output);
        assertEquals(0, code);
        String text = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("请输入 0 到 8"));
        assertTrue(text.contains("尚未实现"));
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
        SwingUtilities.invokeAndWait(() -> {
            var panel = new DesktopApp(services).createPanel();
            assertNotNull(panel);
            assertEquals(3, panel.getComponentCount());
        });
    }

    @Test
    void postgresqlDriverIsAvailableAtRuntimeWithoutOpeningAConnection() throws Exception {
        assertTrue(DriverManager.getDriver("jdbc:postgresql://localhost/metro_test")
                .acceptsURL("jdbc:postgresql://localhost/metro_test"));
    }
}
