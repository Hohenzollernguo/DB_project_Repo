package edu.cs307.metro.ui;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.support.LineUpdateSelectionProbe;
import java.awt.Component;
import java.awt.Container;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class LineUpdatePanelTest {
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

    private static LineUpdatePanel panel(LineUpdateSelectionProbe probe) throws Exception {
        AtomicReference<LineUpdatePanel> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            LineUpdatePanel panel = new LineUpdatePanel(probe.service());
            component(panel, "lineNumber", JTextField.class).setText("13");
            result.set(panel);
        });
        return result.get();
    }

    private static CountDownLatch watchCompletion(LineUpdatePanel panel) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> component(panel, "searchLine", JButton.class)
                .addPropertyChangeListener("enabled", event -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    if (Boolean.TRUE.equals(event.getNewValue())) completed.countDown();
                }));
        return completed;
    }

    private static void search(LineUpdatePanel panel) throws Exception {
        CountDownLatch completed = watchCompletion(panel);
        SwingUtilities.invokeAndWait(() -> component(panel, "searchLine", JButton.class).doClick());
        assertTrue(completed.await(5, TimeUnit.SECONDS));
    }

    private static void enterNewNames(LineUpdatePanel panel) {
        component(panel, "chineseName", JTextField.class).setText(" 新线路 ");
        component(panel, "englishName", JTextField.class).setText(" New Line ");
    }

    @Test
    void searchAndSaveRunOffEdtAndUseTheResolvedId() throws Exception {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        probe.beforeSearch = () -> assertFalse(SwingUtilities.isEventDispatchThread());
        CountDownLatch enteredSql = new CountDownLatch(1);
        CountDownLatch releaseSql = new CountDownLatch(1);
        probe.writes.beforeExecute = () -> {
            assertFalse(SwingUtilities.isEventDispatchThread());
            enteredSql.countDown();
            try {
                assertTrue(releaseSql.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        };
        LineUpdatePanel panel = panel(probe);
        search(panel);
        CountDownLatch completed = watchCompletion(panel);
        try {
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("13号线", component(panel, "chineseName", JTextField.class).getText());
                enterNewNames(panel);
                component(panel, "updateLine", JButton.class).doClick();
            });
            assertTrue(enteredSql.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(component(panel, "updateLine", JButton.class).isEnabled());
                assertFalse(component(panel, "lineNumber", JTextField.class).isEnabled());
                assertFalse(component(panel, "searchLine", JButton.class).isEnabled());
                assertEquals("正在保存…", component(panel, "status", JLabel.class).getText());
            });
        } finally {
            releaseSql.countDown();
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(component(panel, "status", JLabel.class).getText().contains("修改成功：ID=42"));
            assertEquals("新线路", component(panel, "chineseName", JTextField.class).getText());
            assertEquals("New Line", component(panel, "englishName", JTextField.class).getText());
            assertTrue(component(panel, "lineNumber", JTextField.class).isEnabled());
            assertEquals(0, component(panel, "lineCandidates", JComboBox.class).getItemCount());
            assertFalse(component(panel, "updateLine", JButton.class).isEnabled());
        });
        assertEquals(List.of("13"), probe.searches);
        assertEquals(42L, probe.writes.parameters.get(3));
        assertTrue(probe.writes.events.contains("commit"));
    }

    @Test
    void multipleCandidatesRequireAnExplicitSelectionAndExposeTheirIds() throws Exception {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        LineView extension = new LineView(99, "13号线北延", "Line 13 Phase 2 North Extension");
        probe.candidates = List.of(probe.candidates.get(0), extension);
        LineUpdatePanel panel = panel(probe);
        search(panel);
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> choices = component(panel, "lineCandidates", JComboBox.class);
            assertEquals(2, choices.getItemCount());
            assertEquals(-1, choices.getSelectedIndex());
            assertFalse(component(panel, "updateLine", JButton.class).isEnabled());
            assertEquals("", component(panel, "chineseName", JTextField.class).getText());
            assertFalse(component(panel, "chineseName", JTextField.class).isEnabled());
            @SuppressWarnings("unchecked")
            ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) choices.getRenderer();
            JLabel rendered = (JLabel) renderer.getListCellRendererComponent(
                    new JList<>(), choices.getItemAt(1), 1, false, false);
            assertTrue(rendered.getText().contains("ID=99"));
            assertTrue(rendered.getText().contains(extension.englishName()));
            choices.setSelectedIndex(1);
            assertEquals(extension.chineseName(), component(panel, "chineseName", JTextField.class).getText());
            assertTrue(component(panel, "updateLine", JButton.class).isEnabled());
            enterNewNames(panel);
        });
        CountDownLatch completed = watchCompletion(panel);
        SwingUtilities.invokeAndWait(() -> component(panel, "updateLine", JButton.class).doClick());
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals(99L, probe.writes.parameters.get(3));
    }

    @Test
    void changingLineNumberInvalidatesTheOldSelection() throws Exception {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        LineUpdatePanel panel = panel(probe);
        search(panel);
        SwingUtilities.invokeAndWait(() -> {
            component(panel, "lineNumber", JTextField.class).setText("1");
            assertEquals(0, component(panel, "lineCandidates", JComboBox.class).getItemCount());
            assertFalse(component(panel, "updateLine", JButton.class).isEnabled());
            component(panel, "updateLine", JButton.class).doClick();
        });
        assertTrue(probe.writes.events.isEmpty());
    }

    @Test
    void noMatchesDisableSavingAndReportTheNumber() throws Exception {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        probe.candidates = List.of();
        LineUpdatePanel panel = panel(probe);
        search(panel);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(component(panel, "status", JLabel.class).getText().contains("未找到编号为 13"));
            assertFalse(component(panel, "updateLine", JButton.class).isEnabled());
        });
        assertTrue(probe.writes.events.isEmpty());
    }

    @Test
    void businessFailureShowsSafeErrorAndAllowsRetry() throws Exception {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        probe.writes.executionFailure = new SQLException("private SQL detail", "23505");
        LineUpdatePanel panel = panel(probe);
        search(panel);
        CountDownLatch completed = watchCompletion(panel);
        SwingUtilities.invokeAndWait(() -> {
            enterNewNames(panel);
            component(panel, "updateLine", JButton.class).doClick();
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            String status = component(panel, "status", JLabel.class).getText();
            assertTrue(status.contains("[DUPLICATE]"));
            assertFalse(status.contains("private SQL detail"));
            assertTrue(component(panel, "updateLine", JButton.class).isEnabled());
            assertEquals(" 新线路 ", component(panel, "chineseName", JTextField.class).getText());
        });
        assertTrue(probe.writes.events.contains("rollback"));
    }

    @Test
    void failedSearchClearsPreviousCandidates() throws Exception {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        LineUpdatePanel panel = panel(probe);
        search(panel);
        probe.searchFailure = new AppException(ErrorCode.DB_ERROR, "查询失败");
        search(panel);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(component(panel, "status", JLabel.class).getText().contains("[DB_ERROR]"));
            assertEquals(0, component(panel, "lineCandidates", JComboBox.class).getItemCount());
            assertFalse(component(panel, "updateLine", JButton.class).isEnabled());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1%", ""})
    void malformedNumberFailsOnTheFormWithoutSearching(String number) throws Exception {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        LineUpdatePanel panel = panel(probe);
        SwingUtilities.invokeAndWait(() -> {
            component(panel, "lineNumber", JTextField.class).setText(number);
            component(panel, "searchLine", JButton.class).doClick();
            assertTrue(component(panel, "status", JLabel.class).getText().contains("[INVALID_INPUT]"));
            assertFalse(component(panel, "updateLine", JButton.class).isEnabled());
        });
        assertTrue(probe.searches.isEmpty());
        assertTrue(probe.writes.events.isEmpty());
    }

    @Test
    void desktopExposesAllImplementedLineOperationsWithoutOpeningADatabase() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = new DesktopApp(AppBootstrap.create(
                    () -> { throw new AssertionError("Unexpected database access"); })).createPanel();
            JPanel modules = (JPanel) panel.getComponent(1);
            JButton entry = (JButton) modules.getComponent(1);
            assertEquals("修改线路名称", entry.getText());
            assertTrue(entry.isEnabled());
            assertEquals(1, entry.getActionListeners().length);
            JButton create = component(panel, "createLineEntry", JButton.class);
            JButton delete = component(panel, "deleteLineEntry", JButton.class);
            assertNotNull(create);
            assertNotNull(delete);
            assertEquals("添加线路", create.getText());
            assertEquals("删除线路", delete.getText());
            assertTrue(create.isEnabled());
            assertTrue(delete.isEnabled());
            assertEquals(1, create.getActionListeners().length);
            assertEquals(1, delete.getActionListeners().length);
        });
    }
}
