package edu.cs307.metro.ui;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.model.LineDraft;
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

/** 按线路编号查找并选择具体线路，查询和保存均在后台线程执行。 */
public final class LineUpdatePanel extends JPanel {
    private final JTextField lineNumber = new JTextField(12);
    private final JButton search = new JButton("查询线路");
    private final JComboBox<LineView> candidates = new JComboBox<>();
    private final JTextField chineseName = new JTextField(24);
    private final JTextField englishName = new JTextField(24);
    private final JButton update = new JButton("保存修改");
    private final JLabel status = new JLabel("请输入线路编号并查询，再选择要修改的线路。");
    private boolean busy;

    public LineUpdatePanel(LineService lines) {
        super(new BorderLayout(12, 12));
        Objects.requireNonNull(lines);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        lineNumber.setName("lineNumber");
        search.setName("searchLine");
        candidates.setName("lineCandidates");
        chineseName.setName("chineseName");
        englishName.setName("englishName");
        update.setName("updateLine");
        status.setName("status");
        candidates.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean selected, boolean focused) {
                String text = value instanceof LineView line
                        ? "ID=" + line.lineId() + "，" + line.chineseName() + " / " + line.englishName()
                        : "请选择要修改的线路";
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
        });
        JPanel query = new JPanel(new BorderLayout(8, 8));
        query.add(lineNumber, BorderLayout.CENTER);
        query.add(search, BorderLayout.EAST);
        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        addField(form, "线路编号", query);
        addField(form, "匹配线路（ID / 名称）", candidates);
        addField(form, "新中文名", chineseName);
        addField(form, "新英文名", englishName);
        add(form, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.add(update, BorderLayout.NORTH);
        footer.add(status, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        lineNumber.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) { invalidateSelection(); }

            @Override
            public void removeUpdate(DocumentEvent event) { invalidateSelection(); }

            @Override
            public void changedUpdate(DocumentEvent event) { invalidateSelection(); }
        });
        candidates.addActionListener(event -> {
            if (candidates.getSelectedItem() instanceof LineView line) {
                chineseName.setText(line.chineseName());
                englishName.setText(line.englishName());
            } else {
                chineseName.setText("");
                englishName.setText("");
            }
            update.setEnabled(!busy && candidates.getSelectedItem() != null);
            chineseName.setEnabled(update.isEnabled());
            englishName.setEnabled(update.isEnabled());
        });
        search.addActionListener(event -> search(lines));
        update.addActionListener(event -> submit(lines));
        setBusy(false);
    }

    private static void addField(JPanel form, String name, JComponent field) {
        JLabel label = new JLabel(name);
        label.setLabelFor(field);
        form.add(label);
        form.add(field);
    }

    private void invalidateSelection() {
        candidates.removeAllItems();
        update.setEnabled(false);
        status.setText("请输入线路编号并查询，再选择要修改的线路。");
    }

    private void search(LineService lines) {
        String number = lineNumber.getText().trim();
        invalidateSelection();
        if (!number.matches("[1-9][0-9]*")) {
            status.setText("[INVALID_INPUT] 线路编号必须是正整数。");
            return;
        }
        setBusy(true);
        status.setText("正在查询…");
        new SwingWorker<List<LineView>, Void>() {
            @Override
            protected List<LineView> doInBackground() {
                return lines.search(number);
            }

            @Override
            protected void done() {
                try {
                    List<LineView> found = get();
                    // 输入被程序修改时也要丢弃过期查询结果。
                    if (!number.equals(lineNumber.getText().trim())) return;
                    for (LineView line : found) candidates.addItem(line);
                    candidates.setSelectedIndex(found.size() == 1 ? 0 : -1);
                    if (found.isEmpty()) {
                        status.setText("未找到编号为 " + number + " 的线路。");
                    } else if (found.size() == 1) {
                        status.setText("已选中匹配线路，请编辑名称后保存。");
                    } else {
                        status.setText("匹配多条线路，请按 ID 和名称选择要修改的一条。");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    status.setText("查询已中断，请重新查询。");
                } catch (ExecutionException failure) {
                    showFailure(failure, "查询");
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void submit(LineService lines) {
        if (!(candidates.getSelectedItem() instanceof LineView selected)) {
            status.setText("请先查询并选择要修改的线路。");
            return;
        }
        LineDraft input = new LineDraft(chineseName.getText(), englishName.getText());
        setBusy(true);
        status.setText("正在保存…");
        new SwingWorker<LineView, Void>() {
            @Override
            protected LineView doInBackground() {
                return lines.update(selected.lineId(), input);
            }

            @Override
            protected void done() {
                try {
                    LineView result = get();
                    candidates.removeAllItems();
                    chineseName.setText(result.chineseName());
                    englishName.setText(result.englishName());
                    status.setText("修改成功：ID=" + result.lineId() + "，"
                            + result.chineseName() + " / " + result.englishName());
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    status.setText("保存已中断，请重新查询线路确认结果。");
                } catch (ExecutionException failure) {
                    showFailure(failure, "保存");
                } finally {
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
        this.busy = busy;
        search.setEnabled(!busy);
        lineNumber.setEnabled(!busy);
        candidates.setEnabled(!busy && candidates.getItemCount() > 0);
        update.setEnabled(!busy && candidates.getSelectedItem() != null);
        chineseName.setEnabled(update.isEnabled());
        englishName.setEnabled(update.isEnabled());
    }
}
