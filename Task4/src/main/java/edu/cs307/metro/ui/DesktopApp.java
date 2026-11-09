package edu.cs307.metro.ui;

import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.db.DatabaseStatus;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.concurrent.ExecutionException;
import javax.swing.*;

/** 线路添加、修改、删除与数据库诊断入口。数据库请求放入 SwingWorker。 */
public final class DesktopApp {
    private final AppServices services;

    public DesktopApp(AppServices services) {
        this.services = services;
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("CS307 地铁管理系统");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setContentPane(createPanel());
            frame.setSize(720, 360);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    public JPanel createPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(new JLabel("CS307 地铁管理系统 · 线路添加、修改和删除"), BorderLayout.NORTH);
        JPanel modules = new JPanel(new GridLayout(3, 3, 10, 10));
        for (String name : new String[]{"车站管理", "线路管理", "站线关系", "两站路径", "地标路线", "票价查询"}) {
            boolean lineManagement = "线路管理".equals(name);
            JButton button = new JButton(lineManagement ? "修改线路名称" : name + "（待实现）");
            button.setEnabled(lineManagement);
            if (lineManagement) {
                button.addActionListener(event -> showLineDialog(panel, "修改线路名称",
                        new LineUpdatePanel(services.lines())));
            }
            modules.add(button);
        }
        JButton createLine = new JButton("添加线路");
        createLine.setName("createLineEntry");
        createLine.addActionListener(event -> showLineDialog(panel, "添加线路",
                new LineCreatePanel(services.lines())));
        modules.add(createLine);
        JButton deleteLine = new JButton("删除线路");
        deleteLine.setName("deleteLineEntry");
        deleteLine.addActionListener(event -> showLineDialog(panel, "删除线路",
                new LineDeletePanel(services.lines())));
        modules.add(deleteLine);
        panel.add(modules, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        JLabel status = new JLabel("尚未检查数据库连接");
        JButton check = new JButton("检查数据库连接");
        check.addActionListener(event -> {
            check.setEnabled(false);
            status.setText("正在连接…");
            new SwingWorker<DatabaseStatus, Void>() {
                @Override
                protected DatabaseStatus doInBackground() {
                    return services.database().check();
                }

                @Override
                protected void done() {
                    try {
                        DatabaseStatus result = get();
                        status.setText("数据库：" + result.database() + " / schema：" + result.schema());
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        status.setText("连接检查已中断");
                    } catch (ExecutionException failure) {
                        if (failure.getCause() instanceof AppException applicationError) {
                            status.setText("[" + applicationError.code() + "] " + applicationError.getMessage());
                        } else {
                            status.setText("连接检查失败，请查看本地调试信息");
                        }
                    } finally {
                        check.setEnabled(true);
                    }
                }
            }.execute();
        });
        footer.add(check, BorderLayout.WEST);
        footer.add(status, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private static void showLineDialog(JPanel parent, String title, JPanel content) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), title);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}
