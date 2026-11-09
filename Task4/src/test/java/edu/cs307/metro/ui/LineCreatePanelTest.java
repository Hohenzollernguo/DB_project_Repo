package edu.cs307.metro.ui;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.support.LineCreateProbe;
import java.awt.Component;
import java.awt.Container;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LineCreatePanelTest {
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

    private static LineCreatePanel panel(LineCreateProbe probe) throws Exception {
        AtomicReference<LineCreatePanel> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new LineCreatePanel(AppBootstrap.create(probe.connections()).lines());
            component(panel, "chineseName", JTextField.class).setText(" 新线路 ");
            component(panel, "englishName", JTextField.class).setText(" New Line ");
            result.set(panel);
        });
        return result.get();
    }

    private static CountDownLatch watchCompletion(LineCreatePanel panel) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> component(panel, "createLine", JButton.class)
                .addPropertyChangeListener("enabled", event -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    if (Boolean.TRUE.equals(event.getNewValue())) completed.countDown();
                }));
        return completed;
    }

    @Test
    void addingRunsOffEdtDisablesSubmissionAndShowsAssignedId() throws Exception {
        LineCreateProbe probe = new LineCreateProbe();
        CountDownLatch enteredSql = new CountDownLatch(1);
        CountDownLatch releaseSql = new CountDownLatch(1);
        probe.beforeExecute = () -> {
            assertFalse(SwingUtilities.isEventDispatchThread());
            enteredSql.countDown();
            try {
                assertTrue(releaseSql.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        };
        LineCreatePanel panel = panel(probe);
        CountDownLatch completed = watchCompletion(panel);
        try {
            SwingUtilities.invokeAndWait(() -> component(panel, "createLine", JButton.class).doClick());
            assertTrue(enteredSql.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(component(panel, "createLine", JButton.class).isEnabled());
                assertFalse(component(panel, "chineseName", JTextField.class).isEnabled());
                assertEquals("正在添加…", component(panel, "status", JLabel.class).getText());
            });
        } finally {
            releaseSql.countDown();
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(component(panel, "status", JLabel.class).getText().contains("添加成功：ID=42"));
            assertTrue(component(panel, "status", JLabel.class).getText().contains("新线路 / New Line"));
            assertEquals("", component(panel, "chineseName", JTextField.class).getText());
            assertEquals("", component(panel, "englishName", JTextField.class).getText());
            assertTrue(component(panel, "englishName", JTextField.class).isEnabled());
        });
        assertTrue(probe.events.contains("commit"));
    }

    @Test
    void duplicateNameKeepsTheInputsAndAllowsCorrection() throws Exception {
        LineCreateProbe probe = new LineCreateProbe();
        probe.executionFailure = new SQLException("private SQL detail", "23505");
        LineCreatePanel panel = panel(probe);
        CountDownLatch completed = watchCompletion(panel);
        SwingUtilities.invokeAndWait(() -> component(panel, "createLine", JButton.class).doClick());
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            String status = component(panel, "status", JLabel.class).getText();
            assertTrue(status.contains("[DUPLICATE]"));
            assertFalse(status.contains("private SQL detail"));
            assertEquals(" 新线路 ", component(panel, "chineseName", JTextField.class).getText());
            assertTrue(component(panel, "createLine", JButton.class).isEnabled());
        });
    }

    @Test
    void blankNameShowsInvalidInputWithoutOpeningADatabase() throws Exception {
        LineCreateProbe probe = new LineCreateProbe();
        LineCreatePanel panel = panel(probe);
        CountDownLatch completed = watchCompletion(panel);
        SwingUtilities.invokeAndWait(() -> {
            component(panel, "chineseName", JTextField.class).setText(" ");
            component(panel, "createLine", JButton.class).doClick();
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(component(panel, "status", JLabel.class).getText().contains("[INVALID_INPUT]"));
            assertTrue(component(panel, "createLine", JButton.class).isEnabled());
        });
        assertTrue(probe.events.isEmpty());
    }
}
