package edu.cs307.metro.ui;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.LineDraft;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.model.StationDraft;
import edu.cs307.metro.model.StopDraft;
import edu.cs307.metro.support.PostgresTestDatabase;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "METRO_TEST_DB_URL", matches = "jdbc:postgresql://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GuiNumberedLinePostgresTest {
    private PostgresTestDatabase database;
    private AppServices services;

    @BeforeAll
    void createDatabase() throws Exception {
        database = PostgresTestDatabase.create();
        services = AppBootstrap.create(database.connections());
    }

    @BeforeEach
    void reset() throws Exception {
        database.reset();
    }

    @AfterAll
    void closeDatabase() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void actualGuiCreates33And34AfterImportAndNewNumberSupportsStationChanges() throws Exception {
        Path sql = Path.of(System.getProperty("basedir", ".")).resolve("../Task3/src/import.sql");
        try (var connection = database.connections().open(); var statement = connection.createStatement()) {
            statement.execute(Files.readString(sql));
        }
        AtomicInteger inputs = new AtomicInteger();
        AtomicReference<JPanel> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(MetroUiPreview.managementPage(services, false,
                (parent, selected) -> new LineDraft("演示新线路" + inputs.incrementAndGet(), "Demo"))));
        JPanel panel = reference.get();

        clickAndAwait(panel, "managementCreate");
        LineView first = services.lines().search("33").get(0);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = component(panel, "managementResults", JTable.class);
            assertEquals("编号", table.getColumnName(1));
            assertEquals(first.lineId(), table.getValueAt(0, 0));
            assertEquals("33", table.getValueAt(0, 1));
            assertEquals("Line 33 Demo", table.getValueAt(0, 3));
            String message = component(panel, "managementStatus", JLabel.class).getText();
            assertTrue(message.contains("编号=33"), message);
            assertTrue(message.contains("ID=" + first.lineId()), message);
        });
        assertNotEquals(33, first.lineId(), "编号必须取最大线路编号，而不是数据库 ID");

        clickAndAwait(panel, "managementCreate");
        assertEquals("34", services.lines().search("34").get(0).number());
        SwingUtilities.invokeAndWait(() -> component(panel, "lineKeyword", JTextField.class).setText("33"));
        clickAndAwait(panel, "managementSearch");
        SwingUtilities.invokeAndWait(() -> {
            JTable table = component(panel, "managementResults", JTable.class);
            assertEquals(1, table.getRowCount());
            assertEquals(first.lineId(), table.getValueAt(0, 0));
            assertEquals("33", table.getValueAt(0, 1));
        });

        var station = services.stations().create(new StationDraft("编号验证站", "Number Test", "Nanshan"));
        long selectedId = services.lines().search("33").get(0).lineId();
        services.lines().insertStops(selectedId, 1,
                List.of(new StopDraft(station.stationId(), "Operation", 2026, null, null, null, null)));
        assertEquals(station.stationId(), services.lines().getStops(selectedId).get(0).station().stationId());
        services.lines().removeStop(selectedId, station.stationId());
        assertTrue(services.lines().getStops(selectedId).isEmpty());
        // 重新装配服务也能查询，编号没有只保存在本次 GUI 的内存里。
        assertEquals(List.of(first), AppBootstrap.create(database.connections()).lines().search("33"));
    }

    @Test
    void concurrentGuiCreatesCommitDifferentConsecutiveNumbers() throws Exception {
        int count = 6;
        var executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<LineView>>();
            for (int index = 0; index < count; index++) {
                int name = index;
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return services.lines().createNumbered(new LineDraft("并发线路" + name, "Concurrent"));
                }));
            }
            start.countDown();
            Set<String> numbers = new HashSet<>();
            for (var future : futures) {
                LineView line = future.get(15, TimeUnit.SECONDS);
                assertTrue(numbers.add(line.number()));
                assertEquals(List.of(line), services.lines().search(line.number()));
            }
            assertEquals(Set.of("1", "2", "3", "4", "5", "6"), numbers);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void duplicateNameRollsBackAndDoesNotConsumeTheNextNumber() {
        services.lines().create(new LineDraft("原线路", "Line 13 Phase 2"));
        var failure = assertThrows(AppException.class,
                () -> services.lines().createNumbered(new LineDraft("原线路", "Demo")));
        assertEquals(ErrorCode.DUPLICATE, failure.code());
        assertTrue(services.lines().search("14").isEmpty());
        LineView created = services.lines().createNumbered(new LineDraft("新线路", "Line 99 Demo"));
        assertEquals("14", created.number());
        assertEquals("Line 14 Demo", created.englishName());
        assertEquals(List.of(created), services.lines().search("14"));
    }

    private static void clickAndAwait(JPanel panel, String buttonName) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            JButton button = component(panel, buttonName, JButton.class);
            button.addPropertyChangeListener("enabled", event -> {
                if (Boolean.TRUE.equals(event.getNewValue())) completed.countDown();
            });
            button.doClick();
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS), "GUI 后台操作没有完成");
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
