package edu.cs307.metro.ui;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.model.LineDraft;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.service.LineService;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.swing.*;

/** 线路添加表单，复用 Service 校验并在后台提交。 */
public final class LineCreatePanel extends JPanel {
    private final JTextField chineseName = new JTextField(24);
    private final JTextField englishName = new JTextField(24);
    private final JButton create = new JButton("添加线路");
    private final JLabel status = new JLabel("请输入新线路的中英文名。");

    public LineCreatePanel(LineService lines) {
        super(new BorderLayout(12, 12));
        Objects.requireNonNull(lines);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        chineseName.setName("chineseName");
        englishName.setName("englishName");
        create.setName("createLine");
        status.setName("status");
        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        JLabel chineseLabel = new JLabel("中文名");
        chineseLabel.setLabelFor(chineseName);
        form.add(chineseLabel);
        form.add(chineseName);
        JLabel englishLabel = new JLabel("英文名");
        englishLabel.setLabelFor(englishName);
        form.add(englishLabel);
        form.add(englishName);
        add(form, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.add(create, BorderLayout.NORTH);
        footer.add(status, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        create.addActionListener(event -> submit(lines));
    }

    private void submit(LineService lines) {
        LineDraft input = new LineDraft(chineseName.getText(), englishName.getText());
        setBusy(true);
        status.setText("正在添加…");
        new SwingWorker<LineView, Void>() {
            @Override
            protected LineView doInBackground() {
                return lines.create(input);
            }

            @Override
            protected void done() {
                try {
                    LineView result = get();
                    chineseName.setText("");
                    englishName.setText("");
                    status.setText("添加成功：ID=" + result.lineId() + "，"
                            + result.chineseName() + " / " + result.englishName());
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    status.setText("添加已中断，请查询线路确认结果。");
                } catch (ExecutionException failure) {
                    if (failure.getCause() instanceof AppException applicationError) {
                        status.setText("[" + applicationError.code() + "] " + applicationError.getMessage());
                    } else {
                        status.setText("[DB_ERROR] 添加失败，请查看本地调试信息。");
                    }
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        create.setEnabled(!busy);
        chineseName.setEnabled(!busy);
        englishName.setEnabled(!busy);
    }
}
