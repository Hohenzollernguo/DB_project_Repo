package edu.cs307.metro.ui;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.StationView;
import edu.cs307.metro.model.StopDraft;
import edu.cs307.metro.model.StopSource;
import edu.cs307.metro.service.LineService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.*;

/** 选择车站后异步填入已有站线资料，允许调整为目标线路的资料。 */
final class StopInsertPanel extends JPanel implements AutoCloseable {
    record Insertion(int position, StopDraft stop) {}

    private final JTextField stationField;
    private final LineService lines;
    private final JTextField position = field("stopPosition");
    private final JComboBox<StopSource> source = new JComboBox<>();
    private final JComboBox<String> state = new JComboBox<>(new String[]{"Operation", "Construction"});
    private final JTextField year = field("stopYear");
    private final JTextField start = field("stopStart");
    private final JTextField end = field("stopEnd");
    private final JTextField latitude = field("stopLatitude");
    private final JTextField longitude = field("stopLongitude");
    private final JLabel hint = new JLabel("请选择车站，自动读取已有资料。");
    private SwingWorker<List<StopSource>, Void> worker;
    private long requestVersion;
    private boolean ready;
    private boolean closed;

    StopInsertPanel(JTextField stationField, JPanel stationChoice, int nextPosition, LineService lines) {
        super(new BorderLayout(0, 10));
        this.stationField = stationField;
        this.lines = lines;
        position.setText(Integer.toString(nextPosition));
        source.setName("stopSource");
        state.setName("stopState");
        hint.setName("stopHint");
        source.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean selected, boolean focus) {
                Object label = value instanceof StopSource item
                        ? item.line().chineseName() + " / " + item.line().englishName()
                            + "（ID=" + item.line().lineId() + "）" : value;
                return super.getListCellRendererComponent(list, label, index, selected, focus);
            }
        });
        JPanel fields = new JPanel(new GridLayout(9, 2, 8, 8));
        for (Object[] item : new Object[][]{{"车站", stationChoice}, {"插入站序（从 1 开始）", position},
                {"已有资料来源", source}, {"状态", state}, {"开通或预计开通年份", year},
                {"首班时间 HH:mm（可空）", start}, {"末班时间 HH:mm（可空）", end},
                {"源纬度（可空）", latitude}, {"源经度（可空）", longitude}}) {
            fields.add(new JLabel((String) item[0]));
            fields.add((Component) item[1]);
        }
        add(fields, BorderLayout.CENTER);
        add(hint, BorderLayout.SOUTH);
        source.addActionListener(event -> {
            if (source.getSelectedItem() instanceof StopSource selected) fill(selected.attributes());
        });
        stationField.addPropertyChangeListener("selectedStation", event -> loadStation());
        loadStation();
    }

    private static JTextField field(String name) {
        JTextField field = new JTextField(10);
        field.setName(name);
        return field;
    }

    private void loadStation() {
        long version = ++requestVersion;
        if (worker != null) worker.cancel(true);
        setReady(false);
        source.removeAllItems();
        source.setEnabled(false);
        state.setSelectedIndex(0);
        for (JTextField field : details()) field.setText("");
        setDetailsEnabled(false);
        StationView station = (StationView) stationField.getClientProperty("selectedStation");
        if (closed || station == null) {
            hint.setText("请选择车站，自动读取已有资料。");
            return;
        }
        hint.setText("正在读取车站已有资料…");
        worker = new SwingWorker<>() {
            @Override
            protected List<StopSource> doInBackground() {
                return lines.getStopSources(station.stationId());
            }

            @Override
            protected void done() {
                if (closed || version != requestVersion || isCancelled()) return;
                try {
                    List<StopSource> sources = get();
                    for (StopSource item : sources) source.addItem(item);
                    source.setEnabled(sources.size() > 1);
                    setDetailsEnabled(true);
                    hint.setText(sources.isEmpty()
                            ? "该车站暂无站线资料，请补填年份等信息。"
                            : "已自动填入已有资料，可按当前线路调整；换乘站可切换来源。");
                    setReady(true);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    hint.setText("读取已中断，请重新选择车站。");
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    hint.setText(cause instanceof AppException
                            ? "读取失败：" + cause.getMessage() : "读取失败，请检查连接后重新选择车站。");
                }
            }
        };
        worker.execute();
    }

    private void fill(StopDraft stop) {
        state.setSelectedItem(stop.status());
        year.setText(Integer.toString(stop.openingYear()));
        start.setText(stop.startTime() == null ? "" : stop.startTime().toString());
        end.setText(stop.endTime() == null ? "" : stop.endTime().toString());
        latitude.setText(stop.sourceLatitude() == null ? "" : stop.sourceLatitude().toPlainString());
        longitude.setText(stop.sourceLongitude() == null ? "" : stop.sourceLongitude().toPlainString());
    }

    private JTextField[] details() {
        return new JTextField[]{year, start, end, latitude, longitude};
    }

    private void setDetailsEnabled(boolean enabled) {
        state.setEnabled(enabled);
        for (JTextField field : details()) field.setEnabled(enabled);
    }

    boolean isReady() {
        return ready;
    }

    private void setReady(boolean value) {
        boolean previous = ready;
        ready = value;
        firePropertyChange("ready", previous, value);
    }

    Insertion request() {
        StationView station = (StationView) stationField.getClientProperty("selectedStation");
        if (!ready || station == null || closed) {
            throw new AppException(ErrorCode.INVALID_INPUT, "请先选择车站并等待读取完成。");
        }
        int index = Integer.parseInt(position.getText().strip());
        int openingYear = Integer.parseInt(year.getText().strip());
        if (index <= 0 || openingYear <= 0 || openingYear > Short.MAX_VALUE) {
            throw new IllegalArgumentException("站序或年份超出范围");
        }
        return new Insertion(index, new StopDraft(station.stationId(), (String) state.getSelectedItem(), openingYear,
                start.getText().isBlank() ? null : LocalTime.parse(start.getText().strip()),
                end.getText().isBlank() ? null : LocalTime.parse(end.getText().strip()),
                latitude.getText().isBlank() ? null : new BigDecimal(latitude.getText().strip()),
                longitude.getText().isBlank() ? null : new BigDecimal(longitude.getText().strip())));
    }

    Insertion showDialog(Component parent, String title) {
        JButton submit = new JButton("插入");
        JButton cancel = new JButton("取消");
        submit.setEnabled(isReady());
        addPropertyChangeListener("ready", event -> submit.setEnabled(isReady()));
        JOptionPane options = new JOptionPane(this, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{submit, cancel}, submit);
        JDialog dialog = options.createDialog(parent, title);
        Insertion[] result = {null};
        submit.addActionListener(event -> {
            try {
                result[0] = request();
                dialog.dispose();
            } catch (AppException failure) {
                JOptionPane.showMessageDialog(dialog, failure.getMessage(), "输入有误", JOptionPane.ERROR_MESSAGE);
            } catch (IllegalArgumentException | java.time.DateTimeException failure) {
                JOptionPane.showMessageDialog(dialog, "请检查站序、年份、时间（HH:mm）和坐标格式。",
                        "输入有误", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancel.addActionListener(event -> dialog.dispose());
        try {
            dialog.setVisible(true);
            return result[0];
        } finally {
            dialog.dispose();
            close();
        }
    }

    @Override
    public void close() {
        closed = true;
        ++requestVersion;
        if (worker != null) worker.cancel(true);
        setReady(false);
    }
}
