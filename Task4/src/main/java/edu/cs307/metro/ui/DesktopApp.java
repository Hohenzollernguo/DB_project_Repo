package edu.cs307.metro.ui;

import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.db.DatabaseStatus;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.concurrent.ExecutionException;
import javax.swing.*;

/** 界面外壳。数据库请求放入 SwingWorker，界面更新只在 EDT 上执行。 */
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
        panel.add(new JLabel("Task4 开发骨架 · 业务功能待接入"), BorderLayout.NORTH);
        JPanel modules = new JPanel(new GridLayout(2, 3, 10, 10));
        for (String name : new String[]{"车站管理", "线路管理", "站线关系", "两站路径", "地标路线", "票价查询"}) {
            JButton button = new JButton(name + "（待实现）");
            button.setEnabled(false);
            modules.add(button);
        }
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
}
