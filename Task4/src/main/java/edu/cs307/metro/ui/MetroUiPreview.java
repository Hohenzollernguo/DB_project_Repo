package edu.cs307.metro.ui;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.StationView;
import edu.cs307.metro.model.StationDraft;
import edu.cs307.metro.model.LineDraft;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.model.LineNumber;
import edu.cs307.metro.model.LineStopView;
import edu.cs307.metro.model.NeighborPair;
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
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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

/** 地铁管理图形界面。 */
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
        show(AppBootstrap.create());
    }

    public static void show(AppServices services) {
        SwingUtilities.invokeLater(() -> {
            Font font = new Font("Microsoft YaHei", Font.PLAIN, 14);
            UIManager.put("Label.font", font);
            UIManager.put("Button.font", font);
            UIManager.put("TextField.font", font);
            UIManager.put("Table.font", font);
            UIManager.put("TableHeader.font", font);


            JFrame frame = new JFrame("深圳地铁管理系统");
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
        pages.add(managementPage(services, true), "车站管理");
        pages.add(managementPage(services, false), "线路管理");
        pages.add(lineStationPage(services), "站线管理");

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

        String searchPrompt = "线路".equals(itemName) ? "输入线路编号查询，留空显示全部。"
                : "输入" + itemName + "名或 ID 后查询。";
        JLabel status = new JLabel(searchPrompt);
        keyword.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                model.clear();
                status.setText(searchPrompt);
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

    private static JPanel managementPage(AppServices services, boolean stationPage) {
        return managementPage(services, stationPage,
                (parent, selected) -> managementDraft(parent, stationPage, selected));
    }

    // 表单输入与提交分开，允许自动测试直接验证实际管理页面的新增和查询流程。
    static JPanel managementPage(AppServices services, boolean stationPage,
                                 java.util.function.BiFunction<JPanel, Object, Object> draftProvider) {
        String title = stationPage ? "车站管理" : "线路管理";
        String searchHint = stationPage ? "车站中英文名（留空查询全部）" : "线路编号（留空查询全部）";
        String[] columns = stationPage ? new String[]{"ID", "中文名", "英文名", "行政区", "状态"}
                : new String[]{"ID", "编号", "中文名", "英文名"};
        String createLabel = stationPage ? "新增车站" : "新增线路";
        JPanel page = page(title);
        JPanel content = new RoundedPanel(Color.WHITE, 24);
        content.setLayout(new BorderLayout(0, 16));
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        JPanel search = new JPanel(new BorderLayout(10, 0));
        search.setOpaque(false);
        JTextField keyword = textField(20);
        keyword.setName(stationPage ? "stationKeyword" : "lineKeyword");
        keyword.setToolTipText(searchHint);
        search.add(new JLabel(searchHint), BorderLayout.WEST);
        search.add(keyword, BorderLayout.CENTER);
        JButton searchButton = primaryButton("查询");
        searchButton.setName("managementSearch");
        search.add(searchButton, BorderLayout.EAST);
        content.add(search, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setName("managementResults");
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
        JButton createButton = primaryButton(createLabel);
        createButton.setName("managementCreate");
        JButton updateButton = secondaryButton("修改所选");
        JButton deleteButton = secondaryButton("删除所选");
        actions.add(createButton);
        actions.add(updateButton);
        actions.add(deleteButton);
        JLabel status = new JLabel("请输入查询条件，或新增记录。");
        status.setName("managementStatus");
        status.setForeground(MUTED);
        actions.add(status);
        content.add(actions, BorderLayout.SOUTH);
        page.add(content, BorderLayout.CENTER);

        List<Object> results = new ArrayList<>();
        JButton[] buttons = {searchButton, createButton, updateButton, deleteButton};
        Runnable searchAction = () -> {
            if (!searchButton.isEnabled()) return;
            String query = keyword.getText().strip();
            runManagementTask(buttons, status, "正在查询…", () -> stationPage
                    ? services.stations().search(query)
                    : query.isEmpty() ? LineCatalog.listAll() : services.lines().search(query), matches -> {
                results.clear();
                results.addAll(matches);
                results.sort(Comparator.comparingLong(item -> stationPage
                        ? ((StationView) item).stationId() : ((LineView) item).lineId()));
                model.setRowCount(0);
                for (Object item : results) {
                    addManagementRow(model, item);
                }
                status.setText("查询完成，共 " + results.size() + " 条。");
            });
        };
        searchButton.addActionListener(event -> searchAction.run());
        keyword.addActionListener(event -> searchAction.run());

        createButton.addActionListener(event -> {
            Object draft = draftProvider.apply(page, null);
            if (draft == null) return;
            runManagementTask(buttons, status, "正在新增…", () -> stationPage
                    ? services.stations().create((StationDraft) draft)
                    : services.lines().createNumbered((LineDraft) draft), created -> {
                results.clear();
                results.add(created);
                model.setRowCount(0);
                addManagementRow(model, created);
                status.setText(created instanceof LineView line
                        ? "新增成功：编号=" + line.number() + "，ID=" + line.lineId()
                            + "。可在站线管理中输入编号查询。"
                        : "新增成功。");
            });
        });

        updateButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                status.setText("请先从查询结果中选择一行。");
                return;
            }
            Object selected = results.get(table.convertRowIndexToModel(row));
            Object draft = draftProvider.apply(page, selected);
            if (draft == null) return;
            runManagementTask(buttons, status, "正在修改…", () -> stationPage
                    ? services.stations().update(((StationView) selected).stationId(), (StationDraft) draft)
                    : services.lines().update(((LineView) selected).lineId(), (LineDraft) draft), updated -> {
                int index = results.indexOf(selected);
                results.set(index, updated);
                model.removeRow(index);
                insertManagementRow(model, index, updated);
                status.setText("修改成功。");
            });
        });

        deleteButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                status.setText("请先从查询结果中选择一行。");
                return;
            }
            Object selected = results.get(table.convertRowIndexToModel(row));
            String description = stationPage
                    ? ((StationView) selected).key().chineseName() + " · "
                        + ((StationView) selected).key().districtName() + "（ID=" + ((StationView) selected).stationId() + "）"
                    : ((LineView) selected).chineseName() + "（ID=" + ((LineView) selected).lineId() + "）";
            String warning = stationPage ? "删除车站会同时删除其出口及出口地标关联；若仍关联线路则无法删除。"
                    : "线路仍有关联站点时无法删除，请先移除站线关系。";
            if (JOptionPane.showConfirmDialog(page, "确定删除 " + description + " 吗？\n" + warning,
                    "确认删除", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            runManagementTask(buttons, status, "正在删除…", () -> {
                if (stationPage) services.stations().delete(((StationView) selected).stationId());
                else services.lines().delete(((LineView) selected).lineId());
                return selected;
            }, deleted -> {
                int index = results.indexOf(deleted);
                results.remove(index);
                model.removeRow(index);
                status.setText("删除成功。");
            });
        });
        return page;
    }

    private static Object managementDraft(JPanel parent, boolean stationPage, Object selected) {
        StationView station = stationPage && selected != null ? (StationView) selected : null;
        LineView line = !stationPage && selected != null ? (LineView) selected : null;
        JTextField chinese = textField(20);
        JTextField english = textField(20);
        JTextField district = textField(20);
        if (station != null) {
            chinese.setText(station.key().chineseName());
            english.setText(station.englishName());
            district.setText(station.key().districtName());
        } else if (line != null) {
            chinese.setText(line.chineseName());
            english.setText(line.englishName());
        }
        JPanel form = new JPanel(new GridLayout(3, 2, 10, 10));
        if (!stationPage) {
            form.add(new JLabel("线路编号"));
            form.add(new JLabel(line == null ? "自动取当前最大编号 + 1"
                    : line.number().isEmpty() ? "无编号" : line.number()));
        }
        form.add(new JLabel("中文名"));
        form.add(chinese);
        form.add(new JLabel("英文名"));
        form.add(english);
        if (!stationPage) english.setToolTipText("保存时自动添加或保留 Line N 编号前缀。");
        if (stationPage) {
            form.add(new JLabel("行政区英文名"));
            form.add(district);
        }
        String title = (selected == null ? "新增" : "修改") + (stationPage ? "车站" : "线路");
        if (JOptionPane.showConfirmDialog(parent, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        if (stationPage) return new StationDraft(chinese.getText(), english.getText(), district.getText());
        String englishName = english.getText();
        if (line != null && !line.number().isEmpty() && !englishName.isBlank()) {
            englishName = LineNumber.apply(line.number(), englishName.strip());
        }
        return new LineDraft(chinese.getText(), englishName);
    }

    private static void addManagementRow(DefaultTableModel model, Object item) {
        insertManagementRow(model, model.getRowCount(), item);
    }

    private static void insertManagementRow(DefaultTableModel model, int index, Object item) {
        if (item instanceof StationView station) {
            model.insertRow(index, new Object[]{station.stationId(), station.key().chineseName(),
                    station.englishName(), station.key().districtName(), station.state()});
        } else if (item instanceof LineView line) {
            model.insertRow(index, new Object[]{line.lineId(),
                    line.number().isEmpty() ? "—" : line.number(), line.chineseName(), line.englishName()});
        }
    }

    private static <T> void runManagementTask(JButton[] buttons, JLabel status, String message,
                                               java.util.concurrent.Callable<T> work,
                                               java.util.function.Consumer<T> onSuccess) {
        for (JButton button : buttons) button.setEnabled(false);
        status.setText(message);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    status.setText("操作被中断，请查询确认结果。");
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    String detail = cause instanceof AppException ? cause.getMessage() : "请检查连接或输入后重试。";
                    status.setText("操作失败：" + detail);
                    JOptionPane.showMessageDialog(status, detail, "操作失败", JOptionPane.ERROR_MESSAGE);
                } finally {
                    for (JButton button : buttons) button.setEnabled(true);
                }
            }
        }.execute();
    }

    private static JPanel lineStationPage(AppServices services) {
        JPanel page = page("站线管理");
        JPanel content = new RoundedPanel(Color.WHITE, 24);
        content.setLayout(new BorderLayout(0, 16));
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        JTextField lineField = textField(25);
        lineField.setToolTipText("输入线路编号并选择候选，例如 1");
        installSuggestions(lineField, "selectedLine", keyword -> {
            if (!keyword.matches("[1-9][0-9]*")) return List.of();
            List<LineView> matches = new ArrayList<>(services.lines().search(keyword));
            matches.sort(Comparator.comparingLong(LineView::lineId));
            return matches;
        }, line -> "编号 " + line.number() + " · " + line.chineseName() + " / " + line.englishName()
                        + "  (ID: " + line.lineId() + ")",
                line -> "编号 " + line.number() + " · " + line.chineseName() + " / " + line.englishName()
                        + "  (ID: " + line.lineId() + ")");
        JButton chooseLine = secondaryButton("选择线路");
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        top.add(new JLabel("线路"), BorderLayout.WEST);
        top.add(lineField, BorderLayout.CENTER);
        JPanel lineButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lineButtons.setOpaque(false);
        lineButtons.add(chooseLine);
        top.add(lineButtons, BorderLayout.EAST);
        content.add(top, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"站序", "车站 ID", "中文名", "英文名", "行政区", "站线状态", "开通年份"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        table.setRowHeight(42);
        table.setSelectionBackground(new Color(220, 234, 255));
        table.setSelectionForeground(TEXT);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(232, 238, 247));
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setBackground(new Color(232, 240, 252));
        table.getTableHeader().setForeground(TEXT);
        table.getTableHeader().setPreferredSize(new Dimension(0, 40));
        table.setFillsViewportHeight(true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(224, 234, 248)));
        scroll.getViewport().setBackground(Color.WHITE);
        content.add(scroll, BorderLayout.CENTER);

        JButton insert = primaryButton("插入车站");
        JButton remove = secondaryButton("移除所选车站");
        JButton neighbors = secondaryButton("查询前后第 n 站");
        JLabel status = new JLabel("请选择线路。");
        status.setForeground(MUTED);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(insert);
        actions.add(remove);
        actions.add(neighbors);
        actions.add(status);
        content.add(actions, BorderLayout.SOUTH);
        page.add(content, BorderLayout.CENTER);

        List<LineStopView> stops = new ArrayList<>();
        JButton[] buttons = {chooseLine, insert, remove, neighbors};
        chooseLine.addPropertyChangeListener("enabled", event -> lineField.setEditable(chooseLine.isEnabled()));
        Runnable refresh = () -> {
            LineView line = (LineView) lineField.getClientProperty("selectedLine");
            if (line == null) {
                status.setText("请先选择线路。");
                return;
            }
            runManagementTask(buttons, status, "正在读取站序…", () -> services.lines().getStops(line.lineId()), found -> {
                if (!line.equals(lineField.getClientProperty("selectedLine"))) {
                    status.setText("线路已更改，请重新选择线路。");
                    return;
                }
                stops.clear();
                stops.addAll(found);
                model.setRowCount(0);
                for (LineStopView stop : stops) {
                    StationView station = stop.station();
                    model.addRow(new Object[]{stop.position(), station.stationId(), station.key().chineseName(),
                            station.englishName(), station.key().districtName(), stop.attributes().status(),
                            stop.attributes().openingYear()});
                }
                status.setText("共 " + stops.size() + " 站，按线路站序显示。");
            });
        };
        lineField.addPropertyChangeListener("selectedLine", event -> {
            stops.clear();
            model.setRowCount(0);
            if (event.getNewValue() != null && chooseLine.isEnabled()) {
                refresh.run();
            } else {
                status.setText("请选择线路。");
            }
        });
        chooseLine.addActionListener(event -> {
            showSelector(lineField, "线路", "selectedLine", "", keyword -> {
                if (keyword.isBlank()) return LineCatalog.listAll();
                List<LineView> matches = new ArrayList<>(services.lines().search(keyword));
                matches.sort(Comparator.comparingLong(LineView::lineId));
                return matches;
            }, line -> line.chineseName() + " / " + line.englishName() + "  (ID: " + line.lineId() + ")",
                    line -> line.chineseName() + " / " + line.englishName() + "  (ID: " + line.lineId() + ")");
        });

        insert.addActionListener(event -> {
            LineView line = (LineView) lineField.getClientProperty("selectedLine");
            if (line == null) { status.setText("请先选择线路。"); return; }
            JTextField stationField = textField(16);
            stationField.setToolTipText("输入车站名称或 ID 并选择候选");
            installSuggestions(stationField, "selectedStation",
                    keyword -> findStationCandidates(services.stations(), keyword),
                    MetroUiPreview::stationLabel,
                    station -> station.key().chineseName() + " · " + station.key().districtName());
            JButton selectStation = secondaryButton("选择车站");
            selectStation.addActionListener(action -> showStationSelector(stationField, services.stations()));
            JPanel stationChoice = new JPanel(new BorderLayout(8, 0));
            stationChoice.add(stationField, BorderLayout.CENTER);
            stationChoice.add(selectStation, BorderLayout.EAST);
            StopInsertPanel form = new StopInsertPanel(stationField, stationChoice,
                    stops.size() + 1, services.lines());
            StopInsertPanel.Insertion request = form.showDialog(page, "向 " + line.chineseName() + " 插入车站");
            if (request == null) return;
            runManagementTask(buttons, status, "正在插入…", () -> {
                services.lines().insertStops(line.lineId(), request.position(), List.of(request.stop()));
                return null;
            }, result -> {
                stops.clear();
                model.setRowCount(0);
                status.setText("插入成功，正在刷新站序。");
                SwingUtilities.invokeLater(refresh);
            });
        });
        remove.addActionListener(event -> {
            LineView line = (LineView) lineField.getClientProperty("selectedLine");
            int row = table.getSelectedRow();
            if (line == null || row < 0) { status.setText("请先选择线路和要移除的站点。"); return; }
            LineStopView selected = stops.get(table.convertRowIndexToModel(row));
            StationView station = selected.station();
            String prompt = "从 " + line.chineseName() + "（ID=" + line.lineId() + "）移除 "
                    + station.key().chineseName() + " · " + station.key().districtName()
                    + "（ID=" + station.stationId() + "）？\n车站本身不会被删除，后续站序会调整。";
            if (JOptionPane.showConfirmDialog(page, prompt, "确认移除站线关系",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            runManagementTask(buttons, status, "正在移除…", () -> {
                services.lines().removeStop(line.lineId(), station.stationId());
                return null;
            }, result -> {
                stops.clear();
                model.setRowCount(0);
                status.setText("移除成功，正在刷新站序。");
                SwingUtilities.invokeLater(refresh);
            });
        });
        neighbors.addActionListener(event -> {
            LineView line = (LineView) lineField.getClientProperty("selectedLine");
            int row = table.getSelectedRow();
            if (line == null || row < 0) { status.setText("请先选择线路和车站。"); return; }
            StationView station = stops.get(table.convertRowIndexToModel(row)).station();
            showNeighborDialog(page, services, line, station);
        });
        return page;
    }

    private static void showNeighborDialog(JPanel parent, AppServices services, LineView line, StationView station) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "前后站查询",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));

        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 6));
        heading.setOpaque(false);
        JLabel title = new JLabel("查询前后站点");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(TEXT);
        JLabel subtitle = new JLabel(line.chineseName() + "  ·  " + station.key().chineseName()
                + " / " + station.englishName());
        subtitle.setForeground(MUTED);
        heading.add(title);
        heading.add(subtitle);
        content.add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setOpaque(false);
        inputRow.add(new JLabel("相隔站数 n"), BorderLayout.WEST);
        JTextField count = textField(8);
        count.setText("1");
        inputRow.add(count, BorderLayout.CENTER);
        JButton query = primaryButton("查询");
        inputRow.add(query, BorderLayout.EAST);
        center.add(inputRow, BorderLayout.NORTH);

        JTextArea result = new JTextArea("输入非负整数，0 表示当前站。\n查询结果将在这里显示。");
        result.setEditable(false);
        result.setLineWrap(true);
        result.setWrapStyleWord(true);
        result.setBackground(BACKGROUND);
        result.setForeground(TEXT);
        result.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        result.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JScrollPane resultScroll = new JScrollPane(result);
        resultScroll.setBorder(null);
        resultScroll.getViewport().setBackground(BACKGROUND);
        center.add(resultScroll, BorderLayout.CENTER);
        content.add(center, BorderLayout.CENTER);

        JButton close = secondaryButton("关闭");
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.add(close);
        content.add(footer, BorderLayout.SOUTH);
        close.addActionListener(event -> dialog.dispose());
        query.addActionListener(event -> {
            final int n;
            try {
                n = Integer.parseInt(count.getText().strip());
                if (n < 0) throw new NumberFormatException();
            } catch (NumberFormatException failure) {
                result.setText("请输入有效的非负整数。");
                return;
            }
            query.setEnabled(false);
            count.setEditable(false);
            result.setText("正在查询…");
            new SwingWorker<String[], Void>() {
                @Override
                protected String[] doInBackground() {
                    NeighborPair pair = services.neighbors().get(station.englishName(), line.englishName(), n);
                    List<LineStopView> currentStops = services.lines().getStops(line.lineId());
                    LineStopView current = currentStops.stream()
                            .filter(stop -> stop.station().stationId() == station.stationId())
                            .findFirst().orElse(null);
                    return new String[]{formatNeighbor(pair.lowerPositionName(), currentStops, current, -(long) n),
                            formatNeighbor(pair.higherPositionName(), currentStops, current, (long) n)};
                }

                @Override
                protected void done() {
                    if (!dialog.isDisplayable()) return;
                    try {
                        String[] names = get();
                        result.setText("向前第 " + n + " 站\n" + names[0]
                                + "\n\n向后第 " + n + " 站\n" + names[1]);
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        result.setText("查询已中断，请重试。");
                    } catch (ExecutionException failure) {
                        Throwable cause = failure.getCause();
                        result.setText(cause instanceof AppException ? cause.getMessage() : "查询失败，请检查数据库连接。");
                    } finally {
                        query.setEnabled(true);
                        count.setEditable(true);
                    }
                }
            }.execute();
        });
        count.addActionListener(event -> query.doClick());
        dialog.setContentPane(content);
        dialog.setMinimumSize(new Dimension(540, 430));
        dialog.setSize(620, 480);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static String formatNeighbor(String englishName, List<LineStopView> stops,
                                         LineStopView current, long offset) {
        if (englishName == null) return "无此站（超出线路范围）";
        if (current != null) {
            long targetPosition = (long) current.position() + offset;
            for (LineStopView stop : stops) {
                StationView station = stop.station();
                if (stop.position() == targetPosition && englishName.equals(station.englishName())) {
                    return station.key().chineseName() + " / " + station.englishName();
                }
            }
        }
        return englishName + "（站序可能已变化，请刷新后重试）";
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
