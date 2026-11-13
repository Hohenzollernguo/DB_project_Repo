package edu.cs307.metro.ui;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.model.StationKey;
import edu.cs307.metro.model.StationView;
import edu.cs307.metro.model.StopSource;
import edu.cs307.metro.support.PostgresTestDatabase;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "METRO_TEST_DB_URL", matches = "jdbc:postgresql://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StopInsertPostgresTest {
    private PostgresTestDatabase database;
    private AppServices services;

    @BeforeAll
    void open() throws Exception {
        database = PostgresTestDatabase.create();
        services = AppBootstrap.create(database.connections());
    }

    @BeforeEach
    void seed() throws Exception {
        database.reset();
        try (var connection = database.connections().open(); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO public.district (district_id, name_en) VALUES (1, 'Test');
                    INSERT INTO public.station (station_id, chinese_name, english_name, district_id)
                    VALUES (101, '甲', 'A', 1), (102, '乙', 'B', 1), (103, '丙', 'C', 1),
                           (104, '换乘站', 'Transfer', 1), (105, '新站', 'New', 1);
                    INSERT INTO public.metro_line (line_id, chinese_name, english_name)
                    VALUES (31, '原线路', 'Line 1'), (32, '在建线路', 'Line 2'),
                           (33, '运营线路', 'Line 3'), (99, '目标线路', 'Line 99');
                    INSERT INTO public.line_station
                        (line_id, station_id, position, status, opening_year,
                         start_time, end_time, source_latitude, source_longitude)
                    VALUES (31, 101, 1, 'Operation', 2004, '06:30:15', '23:45', 113.123456, 22.654321),
                           (32, 104, 1, 'Construction', 2028, null, null, null, null),
                           (33, 104, 1, 'Operation', 2020, '06:00', '23:00', 114.1, 22.5),
                           (99, 103, 1, 'Operation', 2026, null, null, null, null),
                           (99, 102, 2, 'Operation', 2026, null, null, null, null);
                    """);
        }
    }

    @AfterAll
    void close() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void existingStationCanBeInsertedWithoutRetypingAnyStoredAttributes() throws Exception {
        var original = services.lines().getStops(31);
        List<StopSource> sources = services.lines().getStopSources(101);
        assertEquals(1, sources.size());
        StopSource source = sources.get(0);
        assertEquals("Line 1", source.line().englishName());
        assertEquals(2004, source.attributes().openingYear());
        assertEquals(LocalTime.of(6, 30, 15), source.attributes().startTime());
        assertEquals(new BigDecimal("113.123456"), source.attributes().sourceLatitude());
        assertEquals(new BigDecimal("22.654321"), source.attributes().sourceLongitude());

        StopInsertPanel panel = select(original.get(0).station(), 2);
        try {
            StopInsertPanel.Insertion request = request(panel);
            assertEquals(source.attributes(), request.stop());
            services.lines().insertStops(99, request.position(), List.of(request.stop()));
            var inserted = services.lines().getStops(99);
            assertEquals(List.of(103L, 101L, 102L), inserted.stream().map(s -> s.station().stationId()).toList());
            assertEquals(List.of(1, 2, 3), inserted.stream().map(s -> s.position()).toList());
            assertEquals(source.attributes(), inserted.get(1).attributes());
            assertEquals(original, services.lines().getStops(31));
        } finally {
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    @Test
    void transferStationKeepsSeparateSourcesAndCanCopyTheChosenConstructionRecord() throws Exception {
        var before = services.lines().getStops(32);
        var sources = services.lines().getStopSources(104);
        assertEquals(List.of(33L, 32L), sources.stream().map(source -> source.line().lineId()).toList());
        StopInsertPanel panel = select(before.get(0).station(), 3);
        try {
            assertEquals(sources.get(0).attributes(), request(panel).stop());
            SwingUtilities.invokeAndWait(() -> component(panel, "stopSource", JComboBox.class).setSelectedIndex(1));
            StopInsertPanel.Insertion request = request(panel);
            assertEquals(sources.get(1).attributes(), request.stop());
            assertEquals("Construction", request.stop().status());
            assertNull(request.stop().startTime());
            assertNull(request.stop().sourceLatitude());
            services.lines().insertStops(99, request.position(), List.of(request.stop()));
            assertEquals(request.stop(), services.lines().getStops(99).get(2).attributes());
            assertEquals(before, services.lines().getStops(32));
        } finally {
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    @Test
    void stationWithoutAnySourceRequiresOnlyMissingInformation() throws Exception {
        assertTrue(services.lines().getStopSources(105).isEmpty());
        StopInsertPanel panel = select(new StationView(105, new StationKey("新站", "Test"), "New", "UNASSIGNED"), 3);
        try {
            SwingUtilities.invokeAndWait(() -> {
                assertThrows(NumberFormatException.class, panel::request);
                component(panel, "stopYear", JTextField.class).setText("2026");
            });
            StopInsertPanel.Insertion request = request(panel);
            services.lines().insertStops(99, request.position(), List.of(request.stop()));
            assertEquals(105, services.lines().getStops(99).get(2).station().stationId());
            assertEquals(2026, services.lines().getStops(99).get(2).attributes().openingYear());
        } finally {
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    private StopInsertPanel select(StationView station, int position) throws Exception {
        AtomicReference<StopInsertPanel> result = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            JTextField stationField = new JTextField();
            JPanel choice = new JPanel();
            choice.add(stationField);
            StopInsertPanel panel = new StopInsertPanel(stationField, choice, position, services.lines());
            panel.addPropertyChangeListener("ready", event -> {
                if (Boolean.TRUE.equals(event.getNewValue())) ready.countDown();
            });
            result.set(panel);
            stationField.putClientProperty("selectedStation", station);
        });
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        return result.get();
    }

    private static StopInsertPanel.Insertion request(StopInsertPanel panel) throws Exception {
        AtomicReference<StopInsertPanel.Insertion> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(panel.request()));
        return result.get();
    }

    private static <T extends Component> T component(Container parent, String name, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container nested) {
                T found = component(nested, name, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
