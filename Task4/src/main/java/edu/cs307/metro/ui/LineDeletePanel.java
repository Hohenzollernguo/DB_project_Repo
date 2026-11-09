package edu.cs307.metro.ui;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.service.LineService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** 按线路编号查询并选择具体线路，确认后在后台执行删除。 */
public final class LineDeletePanel extends JPanel {
    private final JTextField lineNumber = new JTextField(20);
    private final JButton search = new JButton("查询线路");
    private final JComboBox<LineView> candidates = new JComboBox<>();
    private final JCheckBox confirmation = new JCheckBox("我确认删除选中的线路");
    private final JButton delete = new JButton("删除线路");
    private final JLabel status = new JLabel("请输入线路编号并查询，再选择要删除的线路。");

    public LineDeletePanel(LineService lines) {
        super(new BorderLayout(12, 12));
        Objects.requireNonNull(lines);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        lineNumber.setName("lineNumber");
        search.setName("searchLine");
        candidates.setName("lineCandidates");
        confirmation.setName("confirmDelete");
        delete.setName("deleteLine");
        status.setName("status");
        candidates.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean hasFocus) {
                super.getListCellRendererComponent(list, value, index, selected, hasFocus);
                if (value instanceof LineView line) {
                    setText("ID=" + line.lineId() + "，" + line.chineseName() + " / " + line.englishName());
                }
                return this;
            }
        });
        JPanel lookup = new JPanel(new BorderLayout(8, 8));
        JLabel label = new JLabel("线路编号");
        label.setLabelFor(lineNumber);
        lookup.add(label, BorderLayout.WEST);
        lookup.add(lineNumber, BorderLayout.CENTER);
        lookup.add(search, BorderLayout.EAST);
        JPanel form = new JPanel(new GridLayout(3, 1, 8, 8));
        form.add(lookup);
        form.add(candidates);
        form.add(confirmation);
        add(form, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.add(delete, BorderLayout.NORTH);
        footer.add(status, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        lineNumber.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) { clearSelection(); }

            @Override
            public void removeUpdate(DocumentEvent event) { clearSelection(); }

            @Override
            public void changedUpdate(DocumentEvent event) { clearSelection(); }
        });
        candidates.addActionListener(event -> confirmation.setSelected(false));
        search.addActionListener(event -> search(lines));
        delete.addActionListener(event -> submit(lines));
    }

    private void clearSelection() {
        candidates.removeAllItems();
        confirmation.setSelected(false);
        status.setText("请输入线路编号并查询，再选择要删除的线路。");
    }

    private void search(LineService lines) {
        String number = lineNumber.getText().trim();
        clearSelection();
        if (!number.matches("[1-9][0-9]*")) {
            status.setText("[INVALID_INPUT] 线路编号必须是正整数。");
            return;
        }
        setBusy(true);
        status.setText("正在查询…");
        new SwingWorker<List<LineView>, Void>() {
            @Override
            protected List<LineView> doInBackground() { return lines.search(number); }

            @Override
            protected void done() {
                try {
                    List<LineView> matches = get();
                    if (!number.equals(lineNumber.getText().trim())) return;
                    for (LineView line : matches) candidates.addItem(line);
                    candidates.setSelectedIndex(matches.size() == 1 ? 0 : -1);
                    status.setText(matches.isEmpty() ? "未找到该编号对应的线路。"
                            : matches.size() == 1 ? "已选中唯一匹配线路，请确认后删除。"
                            : "找到多条线路，请按显示的 ID 选择具体线路，再确认删除。");
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    status.setText("查询已中断，请重试。");
                } catch (ExecutionException failure) {
                    showFailure(failure, "查询");
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void submit(LineService lines) {
        LineView selected = (LineView) candidates.getSelectedItem();
        if (selected == null) {
            status.setText("请先查询并选择要删除的线路。");
            return;
        }
        if (!confirmation.isSelected()) {
            status.setText("请先勾选确认删除。");
            return;
        }
        setBusy(true);
        status.setText("正在删除…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                lines.delete(selected.lineId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    clearSelection();
                    status.setText("删除成功：ID=" + selected.lineId());
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    status.setText("删除已中断，请查询线路确认结果。");
                } catch (ExecutionException failure) {
                    showFailure(failure, "删除");
                } finally {
                    confirmation.setSelected(false);
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void showFailure(ExecutionException failure, String operation) {
        if (failure.getCause() instanceof AppException applicationError) {
            status.setText("[" + applicationError.code() + "] " + applicationError.getMessage());
        } else {
            status.setText("[DB_ERROR] " + operation + "失败，请查看本地调试信息。");
        }
    }

    private void setBusy(boolean busy) {
        search.setEnabled(!busy);
        delete.setEnabled(!busy);
        lineNumber.setEnabled(!busy);
        candidates.setEnabled(!busy);
        confirmation.setEnabled(!busy);
    }
}
