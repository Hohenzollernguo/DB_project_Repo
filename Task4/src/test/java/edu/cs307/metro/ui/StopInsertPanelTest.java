package edu.cs307.metro.ui;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.model.*;
import edu.cs307.metro.service.LineService;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongFunction;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StopInsertPanelTest {
    private static final StationView FIRST = new StationView(101, new StationKey("甲", "Test"), "A", "Operation");
    private static final StationView SECOND = new StationView(102, new StationKey("乙", "Test"), "B", "UNASSIGNED");
    private static final StopDraft FULL = new StopDraft(101, "Operation", 2004,
            LocalTime.of(6, 30, 15), LocalTime.of(23, 45), new BigDecimal("113.123456"), new BigDecimal("22.654321"));

    @Test
    void choosingExistingStationFillsEveryFieldOffEdtAndAllowsImmediateSubmission() throws Exception {
        var panel = create(id -> {
            assertFalse(SwingUtilities.isEventDispatchThread());
            assertEquals(101, id);
            return List.of(source(1, FULL));
        });
        try {
            selectAndAwait(panel, FIRST);
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(new StopInsertPanel.Insertion(3, FULL), panel.request());
                component(panel, "stopYear", JTextField.class).setText("2026");
                assertEquals(2026, panel.request().stop().openingYear());
                assertEquals(2004, FULL.openingYear());
            });
        } finally {
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    @Test
    void switchingSourceReplacesTheEntireRecordIncludingClearingNullableValues() throws Exception {
        StopDraft alternate = new StopDraft(101, "Construction", 2028, null, null, null, null);
        var panel = create(id -> List.of(source(1, FULL), source(2, alternate)));
        try {
            selectAndAwait(panel, FIRST);
            SwingUtilities.invokeAndWait(() -> {
                var source = component(panel, "stopSource", JComboBox.class);
                assertTrue(source.isEnabled());
                source.setSelectedIndex(1);
                assertEquals(alternate, panel.request().stop());
                assertEquals("", component(panel, "stopStart", JTextField.class).getText());
                assertEquals("", component(panel, "stopLatitude", JTextField.class).getText());
            });
        } finally {
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    @Test
    void selectingStationWithoutExistingDataClearsPreviousValuesAndRequiresYear() throws Exception {
        var panel = create(id -> id == 101 ? List.of(source(1, FULL)) : List.of());
        try {
            selectAndAwait(panel, FIRST);
            selectAndAwait(panel, SECOND);
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("", component(panel, "stopYear", JTextField.class).getText());
                assertThrows(NumberFormatException.class, panel::request);
                component(panel, "stopYear", JTextField.class).setText("2026");
                assertEquals(new StopDraft(102, "Operation", 2026, null, null, null, null), panel.request().stop());
            });
        } finally {
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    @Test
    void loadingAndClearingSelectionPreventSubmissionAndOldQueriesCannotOverwriteNewStation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        var panel = create(id -> {
            if (id == 101) {
                entered.countDown();
                try {
                    boolean released = false;
                    while (!released) {
                        try {
                            released = release.await(5, TimeUnit.SECONDS);
                            assertTrue(released);
                        } catch (InterruptedException ignored) {
                            // 模拟无法即时取消的旧数据库请求。
                        }
                    }
                    return List.of(source(1, FULL));
                } finally {
                    finished.countDown();
                }
            }
            return List.of();
        });
        try {
            SwingUtilities.invokeAndWait(() -> {
                stationField(panel).putClientProperty("selectedStation", FIRST);
                assertFalse(panel.isReady());
                assertThrows(AppException.class, panel::request);
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            selectAndAwait(panel, SECOND);
            release.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("", component(panel, "stopYear", JTextField.class).getText());
                component(panel, "stopYear", JTextField.class).setText("2026");
                assertEquals(102, panel.request().stop().stationId());
                stationField(panel).putClientProperty("selectedStation", null);
                assertFalse(panel.isReady());
                assertThrows(AppException.class, panel::request);
            });
        } finally {
            release.countDown();
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    @Test
    void failedReadKeepsSubmissionDisabledAndDoesNotExposePrivateErrors() throws Exception {
        var panel = create(id -> { throw new IllegalStateException("private SQL detail"); });
        CountDownLatch failed = new CountDownLatch(1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                component(panel, "stopHint", JLabel.class).addPropertyChangeListener("text", event -> {
                    if (((String) event.getNewValue()).contains("读取失败")) failed.countDown();
                });
                stationField(panel).putClientProperty("selectedStation", FIRST);
            });
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(panel.isReady());
                assertThrows(AppException.class, panel::request);
                assertFalse(component(panel, "stopHint", JLabel.class).getText().contains("private"));
            });
        } finally {
            SwingUtilities.invokeAndWait(panel::close);
        }
    }

    private static StopSource source(long lineId, StopDraft attributes) {
        return new StopSource(new LineView(lineId, "线路" + lineId, "Line " + lineId), attributes);
    }

    private static StopInsertPanel create(LongFunction<List<StopSource>> read) throws Exception {
        LineService lines = (LineService) Proxy.newProxyInstance(LineService.class.getClassLoader(),
                new Class<?>[]{LineService.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getStopSources")) throw new AssertionError(method.getName());
                    return read.apply((long) args[0]);
                });
        AtomicReference<StopInsertPanel> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JTextField station = new JTextField();
            station.setName("testStation");
            JPanel choice = new JPanel();
            choice.add(station);
            result.set(new StopInsertPanel(station, choice, 3, lines));
        });
        return result.get();
    }

    private static JTextField stationField(StopInsertPanel panel) {
        return component(panel, "testStation", JTextField.class);
    }

    private static void selectAndAwait(StopInsertPanel panel, StationView station) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            panel.addPropertyChangeListener("ready", event -> {
                if (Boolean.TRUE.equals(event.getNewValue())) ready.countDown();
            });
            stationField(panel).putClientProperty("selectedStation", station);
        });
        assertTrue(ready.await(5, TimeUnit.SECONDS));
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
