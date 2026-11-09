package edu.cs307.metro.ui;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.support.LineDeleteSelectionProbe;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LineDeletePanelTest {
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

    private static LineDeletePanel panel(LineDeleteSelectionProbe probe) throws Exception {
        AtomicReference<LineDeletePanel> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            LineDeletePanel panel = new LineDeletePanel(probe.lines());
            component(panel, "lineNumber", JTextField.class).setText("13");
            result.set(panel);
        });
        return result.get();
    }

    private static CountDownLatch click(LineDeletePanel panel, String button) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            JButton target = component(panel, button, JButton.class);
            target.addPropertyChangeListener("enabled", event -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                if (Boolean.TRUE.equals(event.getNewValue())) completed.countDown();
            });
            target.doClick();
        });
        return completed;
    }

    private static void search(LineDeletePanel panel) throws Exception {
        assertTrue(click(panel, "searchLine").await(5, TimeUnit.SECONDS));
    }

    private static void confirm(LineDeletePanel panel) throws Exception {
        SwingUtilities.invokeAndWait(() ->
                component(panel, "confirmDelete", JCheckBox.class).setSelected(true));
    }

    @Test
    void deletionRequiresAResolvedLineAndExplicitConfirmation() throws Exception {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        LineDeletePanel panel = panel(probe);
        SwingUtilities.invokeAndWait(() -> component(panel, "deleteLine", JButton.class).doClick());
        assertTrue(probe.searches.isEmpty());
        assertTrue(probe.deletions.isEmpty());
        search(panel);
        SwingUtilities.invokeAndWait(() -> {
            component(panel, "deleteLine", JButton.class).doClick();
            assertEquals("请先勾选确认删除。", component(panel, "status", JLabel.class).getText());
        });
        assertEquals(List.of("13"), probe.searches);
        assertTrue(probe.deletions.isEmpty());
    }

    @Test
    void uniqueNumberResolvesToItsActualIdAndCannotBeDeletedTwice() throws Exception {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        LineDeletePanel panel = panel(probe);
        search(panel);
        confirm(panel);
        assertTrue(click(panel, "deleteLine").await(5, TimeUnit.SECONDS));
        assertEquals(List.of(42L), probe.deletions);
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("删除成功：ID=42", component(panel, "status", JLabel.class).getText());
            assertEquals(0, component(panel, "lineCandidates", JComboBox.class).getItemCount());
            assertFalse(component(panel, "confirmDelete", JCheckBox.class).isSelected());
            component(panel, "deleteLine", JButton.class).doClick();
        });
        assertEquals(List.of(42L), probe.deletions);
    }

    @Test
    void multipleMatchesRequireAnExplicitChoiceAndDeleteOnlyThatId() throws Exception {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        probe.matches = List.of(new LineView(42, "13号线", "Line 13"),
                new LineView(71, "13号线北延段", "Line 13 Phase 2 North Extension"));
        LineDeletePanel panel = panel(probe);
        search(panel);
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> candidates = component(panel, "lineCandidates", JComboBox.class);
            assertEquals(2, candidates.getItemCount());
            assertEquals(-1, candidates.getSelectedIndex());
            component(panel, "confirmDelete", JCheckBox.class).setSelected(true);
            component(panel, "deleteLine", JButton.class).doClick();
            assertTrue(probe.deletions.isEmpty());
            candidates.setSelectedIndex(1);
            assertFalse(component(panel, "confirmDelete", JCheckBox.class).isSelected());
        });
        confirm(panel);
        assertTrue(click(panel, "deleteLine").await(5, TimeUnit.SECONDS));
        assertEquals(List.of(71L), probe.deletions);
    }

    @Test
    void changingNumberClearsCandidatesAndConfirmation() throws Exception {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        LineDeletePanel panel = panel(probe);
        search(panel);
        confirm(panel);
        SwingUtilities.invokeAndWait(() -> {
            component(panel, "lineNumber", JTextField.class).setText("1");
            assertEquals(0, component(panel, "lineCandidates", JComboBox.class).getItemCount());
            assertFalse(component(panel, "confirmDelete", JCheckBox.class).isSelected());
            component(panel, "deleteLine", JButton.class).doClick();
        });
        assertTrue(probe.deletions.isEmpty());
    }

    @Test
    void invalidNumberDoesNotQueryAndNoMatchDoesNotDelete() throws Exception {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        LineDeletePanel panel = panel(probe);
        SwingUtilities.invokeAndWait(() -> {
            component(panel, "lineNumber", JTextField.class).setText("1%");
            component(panel, "searchLine", JButton.class).doClick();
            assertTrue(component(panel, "status", JLabel.class).getText().contains("[INVALID_INPUT]"));
        });
        assertTrue(probe.searches.isEmpty());
        probe.matches = List.of();
        SwingUtilities.invokeAndWait(() -> component(panel, "lineNumber", JTextField.class).setText("13"));
        search(panel);
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("未找到该编号对应的线路。", component(panel, "status", JLabel.class).getText());
            component(panel, "deleteLine", JButton.class).doClick();
        });
        assertTrue(probe.deletions.isEmpty());
    }

    @Test
    void queryAndDeleteRunOffEdtAndBlockRepeatedSubmission() throws Exception {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        probe.beforeSearch = () -> assertFalse(SwingUtilities.isEventDispatchThread());
        LineDeletePanel panel = panel(probe);
        search(panel);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        probe.beforeDelete = () -> {
            assertFalse(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        };
        confirm(panel);
        CountDownLatch completed = click(panel, "deleteLine");
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                for (String button : List.of("deleteLine", "searchLine")) {
                    assertFalse(component(panel, button, JButton.class).isEnabled());
                }
                assertFalse(component(panel, "lineNumber", JTextField.class).isEnabled());
                assertFalse(component(panel, "lineCandidates", JComboBox.class).isEnabled());
                assertFalse(component(panel, "confirmDelete", JCheckBox.class).isEnabled());
            });
        } finally {
            release.countDown();
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(42L), probe.deletions);
    }

    @Test
    void inUseErrorRequiresFreshConfirmationBeforeRetry() throws Exception {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        probe.deleteFailure = new AppException(ErrorCode.IN_USE, "线路仍有关联站点。");
        LineDeletePanel panel = panel(probe);
        search(panel);
        confirm(panel);
        assertTrue(click(panel, "deleteLine").await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(component(panel, "status", JLabel.class).getText().contains("[IN_USE]"));
            assertFalse(component(panel, "confirmDelete", JCheckBox.class).isSelected());
            component(panel, "deleteLine", JButton.class).doClick();
        });
        assertEquals(List.of(42L), probe.deletions);
    }
}
