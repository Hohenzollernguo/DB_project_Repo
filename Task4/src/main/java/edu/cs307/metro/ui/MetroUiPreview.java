package edu.cs307.metro.ui;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.StationView;
import edu.cs307.metro.model.FareQuote;
import edu.cs307.metro.model.LandmarkHit;
import edu.cs307.metro.model.LandmarkRoute;
import edu.cs307.metro.model.RouteLeg;
import edu.cs307.metro.model.RouteResult;
import edu.cs307.metro.service.StationService;
import edu.cs307.metro.service.LandmarkService;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/** 地铁管理界面的独立布局预览。 */
public final class MetroUiPreview {
    private static final Color BACKGROUND = new Color(241, 246, 253);
    private static final Color BLUE = new Color(36, 104, 219);
    private static final Color SELECTED = new Color(36, 104, 219, 26);
    private static final Color TRANSPARENT = new Color(36, 104, 219, 0);
    private static final Color TEXT = new Color(29, 48, 79);
    private static final Color MUTED = new Color(104, 124, 151);

    private MetroUiPreview() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Font font = new Font("Microsoft YaHei", Font.PLAIN, 14);
            UIManager.put("Label.font", font);
            UIManager.put("Button.font", font);
            UIManager.put("TextField.font", font);
            UIManager.put("Table.font", font);
            UIManager.put("TableHeader.font", font);


            AppServices services = AppBootstrap.create();
            JFrame frame = new JFrame("深圳地铁管理系统 · 界面预览");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setContentPane(createContent(services));
            frame.setMinimumSize(new Dimension(900, 600));
            frame.setSize(1100, 700);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static JPanel createContent(AppServices services) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);

        JLabel title = new JLabel("深圳地铁管理系统");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(TEXT);

        JPanel pages = new JPanel(new CardLayout());
        pages.setBackground(BACKGROUND);
        pages.add(routePage(services), "路线查询");
        pages.add(managementPage("车站管理", "输入车站中文名或英文名",
                new String[]{"ID", "中文名", "英文名", "行政区", "状态"}, "新增车站"), "车站管理");
        pages.add(managementPage("线路管理", "输入线路编号",
                new String[]{"ID", "中文名", "英文名"}, "新增线路"), "线路管理");
        pages.add(lineStationPage(), "站线管理");

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(224, 234, 248)),
                BorderFactory.createEmptyBorder(12, 32, 12, 32)));
        header.add(title, BorderLayout.WEST);
        header.add(navigation(pages, "路线查询", "车站管理", "线路管理", "站线管理"), BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);
        root.add(pages, BorderLayout.CENTER);
        return root;
    }

    private static JPanel navigation(JPanel pages, String... names) {
        CardLayout cardLayout = (CardLayout) pages.getLayout();
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        navigation.setOpaque(false);
        List<JButton> navigationButtons = new ArrayList<>();
        for (String name : names) {
            JButton button = secondaryButton(name);
            button.setBackground(navigationButtons.isEmpty() ? SELECTED : TRANSPARENT);
            button.addActionListener(event -> {
                cardLayout.show(pages, name);
                for (JButton navigationButton : navigationButtons) {
                    navigationButton.setBackground(TRANSPARENT);
                }
                button.setBackground(SELECTED);
            });
            navigationButtons.add(button);
            navigation.add(button);
        }
        return navigation;
    }

    private static JPanel routePage(AppServices services) {
        JPanel page = page("路线查询");
        JPanel forms = new JPanel(new CardLayout());
        forms.setOpaque(false);
        forms.add(routeForm("起点站", "终点站", true, services), "按车站查询");
        forms.add(routeForm("起点地标", "终点地标", false, services), "按地标查询");
        page.add(withTabs(forms, "按车站查询", "按地标查询"), BorderLayout.CENTER);
        return page;
    }

    private static JPanel routeForm(String fromLabel, String toLabel, boolean stationSelection,
                                    AppServices services) {
        JPanel panel = new JPanel(new BorderLayout(20, 0));
        panel.setOpaque(false);

        JPanel form = new RoundedPanel(Color.WHITE, 24);
        form.setLayout(new BorderLayout());
        form.setPreferredSize(new Dimension(350, 0));
        form.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JPanel inputs = new JPanel(new GridBagLayout());
        inputs.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(0, 0, 9, 0);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridy = 0;
        JTextField fromField = addField(inputs, constraints, 0, fromLabel, stationSelection, services);
        JTextField toField = addField(inputs, constraints, 2, toLabel, stationSelection, services);
        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        constraints.insets = new Insets(10, 0, 0, 0);
        JButton query = primaryButton("查询路线");
        inputs.add(query, constraints);
        form.add(inputs, BorderLayout.NORTH);
        panel.add(form, BorderLayout.WEST);

        JPanel result = new RoundedPanel(Color.WHITE, 24);
        result.setLayout(new BorderLayout(0, 15));
        result.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JLabel heading = new JLabel("查询结果");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        result.add(heading, BorderLayout.NORTH);
        JTextArea routeText = new JTextArea("选择起点和终点后查询。");
        routeText.setEditable(false);
        routeText.setLineWrap(true);
        routeText.setWrapStyleWord(true);
        routeText.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        routeText.setForeground(TEXT);
        JScrollPane routeScroll = new JScrollPane(routeText);
        routeScroll.setBorder(null);
        result.add(routeScroll, BorderLayout.CENTER);

        JPanel metrics = new JPanel(new GridLayout(1, 3, 10, 0));
        metrics.setOpaque(false);
        List<JLabel> metricValues = new ArrayList<>();
        for (String name : new String[]{"站间段数", "换乘次数", "项目票价"}) {
            JPanel metric = new RoundedPanel(BACKGROUND, 18);
            metric.setLayout(new BorderLayout(0, 5));
            metric.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            JLabel value = new JLabel("—");
            value.setFont(value.getFont().deriveFont(Font.BOLD, 20f));
            value.setForeground(BLUE);
            metricValues.add(value);
            JLabel caption = new JLabel(name);
            caption.setForeground(MUTED);
            metric.add(value, BorderLayout.NORTH);
            metric.add(caption, BorderLayout.SOUTH);
            metrics.add(metric);
        }
        result.add(metrics, BorderLayout.SOUTH);
        panel.add(result, BorderLayout.CENTER);

        query.addActionListener(event -> {
            StationView from = (StationView) fromField.getClientProperty("selectedStation");
            StationView to = (StationView) toField.getClientProperty("selectedStation");
            String fromName = fromField.getText().strip();
            String toName = toField.getText().strip();
            for (JLabel value : metricValues) {
                value.setText("—");
            }
            if (stationSelection && (fromName.isEmpty() || toName.isEmpty())) {
                routeText.setText("请输入起点站和终点站。");
                return;
            }
            if (!stationSelection && (fromName.isEmpty() || toName.isEmpty())) {
                routeText.setText("请输入起点和终点地标全名。");
                return;
            }
            query.setEnabled(false);
            routeText.setText("正在查询…");
            new SwingWorker<RouteDisplay, Void>() {
                @Override
                protected RouteDisplay doInBackground() {
                    if (stationSelection) {
                        StationView start = resolveStation(from, fromName, services.stations());
                        StationView end = resolveStation(to, toName, services.stations());
                        RouteResult route = services.routes().findRoute(start.stationId(), end.stationId());
                        FareQuote fare = services.fares().quote(start.stationId(), end.stationId());
                        return new RouteDisplay(route, fare, start.key().chineseName(), end.key().chineseName());
                    }
                    LandmarkRoute landmarks = services.landmarks().findRoute(fromName, toName);
                    LandmarkHit fromHit = landmarks.from();
                    LandmarkHit toHit = landmarks.to();
                    FareQuote fare = services.fares().quote(fromHit.station().stationId(), toHit.station().stationId());
                    String start = fromHit.landmarkName() + " · " + fromHit.station().key().chineseName()
                            + "站 " + fromHit.exitCode() + "口";
                    String end = toHit.landmarkName() + " · " + toHit.station().key().chineseName()
                            + "站 " + toHit.exitCode() + "口";
                    return new RouteDisplay(landmarks.route(), fare, start, end);
                }

                @Override
                protected void done() {
                    try {
                        RouteDisplay display = get();
                        StringBuilder text = new StringBuilder();
                        text.append("起点  ").append(display.from()).append("\n\n");
                        for (RouteLeg leg : display.route().legs()) {
                            text.append(leg.line().chineseName()).append("\n");
                            for (int i = 0; i < leg.stations().size(); i++) {
                                if (i > 0) {
                                    text.append("  →  ");
                                }
                                text.append(leg.stations().get(i).key().chineseName());
                            }
                            text.append("\n\n");
                        }
                        text.append("终点  ").append(display.to());
                        routeText.setText(text.toString());
                        routeText.setCaretPosition(0);
                        metricValues.get(0).setText(display.route().stopCount() + " 段");
                        metricValues.get(1).setText(display.route().transferCount() + " 次");
                        metricValues.get(2).setText(display.fare().amount() + " 元");
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        routeText.setText("查询已中断。");
                    } catch (ExecutionException failure) {
                        if (failure.getCause() instanceof AppException applicationError) {
                            routeText.setText("[" + applicationError.code() + "] " + applicationError.getMessage());
                        } else {
                            routeText.setText("查询失败，请检查数据库连接和路线缓存。");
                        }
                    } finally {
                        query.setEnabled(true);
                    }
                }
            }.execute();
        });
        return panel;
    }

    private record RouteDisplay(RouteResult route, FareQuote fare, String from, String to) {
    }

    private static StationView resolveStation(StationView selected, String name, StationService stations) {
        if (selected != null) {
            return selected;
        }
        List<StationView> matches = stations.search(name);
        if (matches.isEmpty()) {
            throw new AppException(ErrorCode.NOT_FOUND, "未找到车站：" + name);
        }
        if (matches.size() > 1) {
            throw new AppException(ErrorCode.AMBIGUOUS_NAME, "“" + name + "”有多个结果，请点击选车站。");
        }
        return matches.get(0);
    }

    private static JTextField addField(JPanel panel, GridBagConstraints constraints, int row,
                                 String label, boolean stationSelection, AppServices services) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(new JLabel(label), constraints);
        constraints.gridy = row + 1;
        constraints.weightx = 1;
        constraints.gridwidth = 1;
        JTextField field = textField(12);
        panel.add(field, constraints);
        if (stationSelection) {
            field.setToolTipText("输入站名并选择候选，或点击选车站按 ID 查询");
            installSuggestions(field, "selectedStation",
                    keyword -> sortedStations(services.stations().search(keyword)),
                    MetroUiPreview::stationLabel,
                    station -> station.key().chineseName() + " · " + station.key().districtName());
            constraints.gridx = 1;
            constraints.weightx = 0;
            constraints.insets = new Insets(0, 8, 9, 0);
            JButton choose = secondaryButton("选车站");
            choose.addActionListener(event -> showStationSelector(field, services.stations()));
            panel.add(choose, constraints);
            constraints.insets = new Insets(0, 0, 9, 0);
        } else {
            field.setToolTipText("输入地标名称并选择候选，或点击选地标按 ID 查询");
            installSuggestions(field, "selectedLandmark",
                    keyword -> uniqueLandmarks(services.landmarks().searchPrefix(keyword)),
                    hit -> hit.landmarkName() + "  (ID: " + hit.landmarkId() + ")",
                    LandmarkHit::landmarkName);
            constraints.gridx = 1;
            constraints.weightx = 0;
            constraints.insets = new Insets(0, 8, 9, 0);
            JButton choose = secondaryButton("选地标");
            choose.addActionListener(event -> showLandmarkSelector(field, services.landmarks()));
            panel.add(choose, constraints);
            constraints.insets = new Insets(0, 0, 9, 0);
        }
        constraints.gridwidth = 1;
        return field;
    }

    private static <T> void installSuggestions(JTextField field, String property,
                                                Function<String, List<T>> lookup,
                                                Function<T, String> label,
                                                Function<T, String> displayValue) {
        JPopupMenu popup = new JPopupMenu();
        popup.setFocusable(false);
        DefaultListModel<T> model = new DefaultListModel<>();
        JList<T> suggestions = new JList<>(model);
        suggestions.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        suggestions.setCellRenderer((list, value, index, selected, focused) -> {
            DefaultListCellRenderer renderer = new DefaultListCellRenderer();
            return renderer.getListCellRendererComponent(list, label.apply(value), index, selected, focused);
        });
        JScrollPane scroll = new JScrollPane(suggestions);
        scroll.setPreferredSize(new Dimension(340, 180));
        popup.add(scroll);

        Runnable choose = () -> {
            T selected = suggestions.getSelectedValue();
            if (selected != null) {
                popup.setVisible(false);
                field.setText(displayValue.apply(selected));
                field.putClientProperty(property, selected);
                field.requestFocusInWindow();
            }
        };
        suggestions.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 1) {
                    int index = suggestions.locationToIndex(event.getPoint());
                    if (index >= 0 && suggestions.getCellBounds(index, index).contains(event.getPoint())) {
                        suggestions.setSelectedIndex(index);
                        choose.run();
                    }
                }
            }
        });
        suggestions.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    choose.run();
                    event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    popup.setVisible(false);
                    field.requestFocusInWindow();
                }
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_DOWN && popup.isVisible() && !model.isEmpty()) {
                    suggestions.setSelectedIndex(0);
                    suggestions.requestFocusInWindow();
                    event.consume();
                }
            }
        });

        int[] generation = {0};
        Timer timer = new Timer(300, event -> {
            String keyword = field.getText().strip();
            if (keyword.isEmpty() || field.getClientProperty(property) != null) {
                popup.setVisible(false);
                return;
            }
            int request = generation[0];
            new SwingWorker<List<T>, Void>() {
                @Override
                protected List<T> doInBackground() {
                    return lookup.apply(keyword);
                }

                @Override
                protected void done() {
                    if (request != generation[0] || !field.isShowing()
                            || field.getClientProperty(property) != null) {
                        return;
                    }
                    try {
                        List<T> results = get();
                        model.clear();
                        for (T result : results) {
                            model.addElement(result);
                        }
                        if (results.isEmpty() || !field.hasFocus()) {
                            popup.setVisible(false);
                            return;
                        }
                        scroll.setPreferredSize(new Dimension(
                                Math.max(field.getWidth(), 340), Math.min(results.size(), 6) * 30 + 4));
                        popup.show(field, 0, field.getHeight());
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException failure) {
                        popup.setVisible(false);
                        field.setToolTipText("候选查询失败，请检查数据库连接。");
                    }
                }
            }.execute();
        });
        timer.setRepeats(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                generation[0]++;
                field.putClientProperty(property, null);
                popup.setVisible(false);
                timer.restart();
            }

            @Override
            public void insertUpdate(DocumentEvent event) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                changed();
            }
        });
    }

    private static String stationLabel(StationView station) {
        return station.key().chineseName() + " · " + station.key().districtName()
                + " / " + station.englishName() + "  (ID: " + station.stationId() + ")";
    }

    private static void showStationSelector(JTextField target, StationService stations) {
        StationView selected = (StationView) target.getClientProperty("selectedStation");
        String initialName = selected == null ? target.getText().strip() : selected.key().chineseName();
        showSelector(target, "车站", "selectedStation", initialName,
                keyword -> findStationCandidates(stations, keyword),
                MetroUiPreview::stationLabel,
                station -> station.key().chineseName() + " · " + station.key().districtName());
    }

    private static void showLandmarkSelector(JTextField target, LandmarkService landmarks) {
        showSelector(target, "地标", "selectedLandmark", target.getText().strip(),
                keyword -> findLandmarkCandidates(landmarks, keyword),
                hit -> hit.landmarkName() + "  (ID: " + hit.landmarkId() + ")",
                LandmarkHit::landmarkName);
    }

    private static <T> void showSelector(JTextField target, String itemName, String property,
                                         String initialName, Function<String, List<T>> lookup,
                                         Function<T, String> label, Function<T, String> displayValue) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(target), "选择" + itemName,
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        JTextField keyword = textField(18);
        keyword.setText(initialName);
        JButton search = primaryButton("查询");
        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.add(keyword, BorderLayout.CENTER);
        searchBar.add(search, BorderLayout.EAST);
        content.add(searchBar, BorderLayout.NORTH);

        DefaultListModel<T> model = new DefaultListModel<>();
        JList<T> candidates = new JList<>(model);
        candidates.setCellRenderer((list, value, index, selected, focused) -> {
            DefaultListCellRenderer renderer = new DefaultListCellRenderer();
            return renderer.getListCellRendererComponent(list, label.apply(value), index, selected, focused);
        });
        content.add(new JScrollPane(candidates), BorderLayout.CENTER);

        JLabel status = new JLabel("输入" + itemName + "名或 ID 后查询。");
        keyword.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                model.clear();
                status.setText("输入" + itemName + "名或 ID 后查询。");
            }

            @Override
            public void insertUpdate(DocumentEvent event) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                changed();
            }
        });
        JButton select = secondaryButton("确定");
        select.addActionListener(event -> {
            T selected = candidates.getSelectedValue();
            if (selected == null) {
                status.setText("请先选择" + itemName + "。");
                return;
            }
            target.setText(displayValue.apply(selected));
            target.putClientProperty(property, selected);
            dialog.dispose();
        });
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(status, BorderLayout.CENTER);
        footer.add(select, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);

        search.addActionListener(event -> {
            String text = keyword.getText().strip();
            search.setEnabled(false);
            select.setEnabled(false);
            keyword.setEditable(false);
            model.clear();
            status.setText("正在查询…");
            new SwingWorker<List<T>, Void>() {
                @Override
                protected List<T> doInBackground() {
                    return lookup.apply(text);
                }

                @Override
                protected void done() {
                    if (!dialog.isDisplayable()) {
                        return;
                    }
                    try {
                        List<T> results = get();
                        for (T result : results) {
                            model.addElement(result);
                        }
                        status.setText(results.isEmpty() ? "未找到" + itemName + "。" : "请选择" + itemName + "。");
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        status.setText("查询已中断。");
                    } catch (ExecutionException failure) {
                        if (failure.getCause() instanceof AppException applicationError) {
                            status.setText("[" + applicationError.code() + "] " + applicationError.getMessage());
                        } else {
                            status.setText("查询失败，请检查数据库连接。");
                        }
                    } finally {
                        search.setEnabled(true);
                        select.setEnabled(true);
                        keyword.setEditable(true);
                    }
                }
            }.execute();
        });
        keyword.addActionListener(event -> search.doClick());
        dialog.setContentPane(content);
        dialog.setSize(520, 400);
        dialog.setLocationRelativeTo(target);
        if (!keyword.getText().isBlank()) {
            SwingUtilities.invokeLater(search::doClick);
        }
        dialog.setVisible(true);
    }

    private static List<StationView> findStationCandidates(StationService stations, String keyword) {
        if (!keyword.matches("[0-9]+")) {
            return sortedStations(stations.search(keyword));
        }

        long stationId;
        try {
            stationId = Long.parseLong(keyword);
        } catch (NumberFormatException failure) {
            return List.of();
        }

        for (StationView station : stations.search("")) {
            if (station.stationId() == stationId) {
                return List.of(station);
            }
        }
        return List.of();
    }

    private static List<StationView> sortedStations(List<StationView> stations) {
        List<StationView> sorted = new ArrayList<>(stations);
        sorted.sort(Comparator.comparingLong(StationView::stationId));
        return sorted;
    }

    private static List<LandmarkHit> uniqueLandmarks(List<LandmarkHit> hits) {
        Map<Long, LandmarkHit> byId = new LinkedHashMap<>();
        List<LandmarkHit> sorted = new ArrayList<>(hits);
        sorted.sort(Comparator.comparingLong(LandmarkHit::landmarkId));
        for (LandmarkHit hit : sorted) {
            byId.putIfAbsent(hit.landmarkId(), hit);
        }
        return new ArrayList<>(byId.values());
    }

    private static List<LandmarkHit> findLandmarkCandidates(LandmarkService landmarks, String keyword) {
        if (!keyword.matches("[0-9]+")) {
            return uniqueLandmarks(landmarks.searchPrefix(keyword));
        }

        long landmarkId;
        try {
            landmarkId = Long.parseLong(keyword);
        } catch (NumberFormatException failure) {
            return List.of();
        }
        for (LandmarkHit hit : landmarks.searchPrefix("")) {
            if (hit.landmarkId() == landmarkId) {
                return List.of(hit);
            }
        }
        return List.of();
    }

    private static JPanel managementPage(String title, String searchHint, String[] columns,
                                         String createLabel) {
        JPanel page = page(title);
        JPanel content = new RoundedPanel(Color.WHITE, 24);
        content.setLayout(new BorderLayout(0, 16));
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        JPanel search = new JPanel(new BorderLayout(10, 0));
        search.setOpaque(false);
        JTextField keyword = textField(20);
        keyword.setToolTipText(searchHint);
        search.add(new JLabel(searchHint), BorderLayout.WEST);
        search.add(keyword, BorderLayout.CENTER);
        search.add(primaryButton("查询"), BorderLayout.EAST);
        content.add(search, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(32);
        table.setSelectionBackground(new Color(220, 234, 255));
        table.setSelectionForeground(TEXT);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(232, 238, 247));
        table.getTableHeader().setBackground(new Color(232, 240, 252));
        table.getTableHeader().setForeground(TEXT);
        table.setFillsViewportHeight(true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(224, 234, 248)));
        scroll.getViewport().setBackground(Color.WHITE);
        content.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(primaryButton(createLabel));
        actions.add(secondaryButton("修改所选"));
        actions.add(secondaryButton("删除所选"));
        content.add(actions, BorderLayout.SOUTH);
        page.add(content, BorderLayout.CENTER);
        return page;
    }

    private static JPanel lineStationPage() {
        JPanel page = page("站线管理");
        JPanel forms = new JPanel(new CardLayout());
        forms.setOpaque(false);
        forms.add(simpleForm("选择线路", "选择车站", "插入位置", "运营状态", "插入车站"), "插入车站");
        forms.add(simpleForm("选择线路", "选择车站", null, null, "移除车站"), "移除车站");
        forms.add(simpleForm("选择线路", "选择车站", "输入 n", null, "查询"), "前后第 n 站");
        page.add(withTabs(forms, "插入车站", "移除车站", "前后第 n 站"), BorderLayout.CENTER);
        return page;
    }

    private static JPanel withTabs(JPanel cards, String... names) {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setOpaque(false);
        panel.add(navigation(cards, names), BorderLayout.NORTH);
        panel.add(cards, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel simpleForm(String first, String second, String third, String fourth,
                                     String buttonLabel) {
        JPanel form = new RoundedPanel(Color.WHITE, 24);
        form.setLayout(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(0, 0, 14, 14);
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        for (String label : new String[]{first, second, third, fourth}) {
            if (label == null) {
                continue;
            }
            constraints.gridx = 0;
            constraints.gridy = row;
            constraints.weightx = 0;
            form.add(new JLabel(label), constraints);
            constraints.gridx = 1;
            constraints.weightx = 1;
            form.add(textField(25), constraints);
            row++;
        }
        constraints.gridx = 1;
        constraints.gridy = row;
        constraints.weightx = 0;
        form.add(primaryButton(buttonLabel), constraints);
        constraints.gridy = row + 1;
        constraints.weighty = 1;
        form.add(Box.createVerticalGlue(), constraints);
        return form;
    }

    private static JPanel page(String heading) {
        JPanel page = new JPanel(new BorderLayout(0, 20));
        page.setBackground(BACKGROUND);
        page.setBorder(BorderFactory.createEmptyBorder(28, 32, 32, 32));
        JLabel label = new JLabel(heading);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 24f));
        label.setForeground(TEXT);
        page.add(label, BorderLayout.NORTH);
        return page;
    }

    private static JButton primaryButton(String label) {
        JButton button = new RoundedButton(label);
        button.setBackground(BLUE);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(9, 20, 9, 20));
        return button;
    }

    private static JButton secondaryButton(String label) {
        JButton button = new RoundedButton(label);
        button.setBackground(SELECTED);
        button.setForeground(BLUE);
        button.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        return button;
    }

    private static JTextField textField(int columns) {
        JTextField field = new JTextField(columns);
        field.setForeground(TEXT);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(198, 212, 231)),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)));
        return field;
    }

    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final int radius;

        private RoundedPanel(Color fill, int radius) {
            this.fill = fill;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setColor(fill);
            canvas.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            canvas.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class RoundedButton extends JButton {
        private RoundedButton(String label) {
            super(label);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = getBackground();
            if (getModel().isRollover()) {
                fill = fill.getAlpha() < 255 ? new Color(36, 104, 219, 38) : fill.darker();
            }
            canvas.setColor(fill);
            canvas.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            canvas.dispose();
            super.paintComponent(graphics);
        }
    }
}
