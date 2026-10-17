package org.example.detector.desktop;

import static org.example.detector.desktop.ReferenceTheme.*;

import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.example.detector.core.*;

/** 按交互界面说明绘制的竖屏电脑入口；耗时采集由 Swing 定时器驱动，避免阻塞界面线程。 */
public final class DesktopApp extends JFrame {
  private final Workstation realDevice = new UnavailableWorkstation();
  private final SimulatedWorkstation benchDevice = new SimulatedWorkstation();
  private Workstation device = benchDevice;
  private final ResultRepository repository;
  final CurveLibrary library;
  final Path dataDirectory;
  JPanel body, footer, screen;
  private Calibration selectedCalibration;
  private DataOrigin selectedOrigin = DataOrigin.UNKNOWN;
  private boolean administrator;
  private javax.swing.Timer timer;
  private DetectionSession session;
  private DetectionResult unsaved;
  private int minutes = 5;
  private final SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  public DesktopApp(Path data) throws IOException {
    super("毒品快速检测仪");
    repository = new FileResultRepository(data);
    dataDirectory = data;
    library = new CurveLibrary(data.resolve("curves"));
    DemoCatalog.ensure(library);
    benchDevice.connect();
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(440, 780);
    setMinimumSize(new Dimension(380, 660));
    setLocationRelativeTo(null);
    home();
  }

  void page(String title, String subtitle, boolean back) {
    hideOverlay();
    screen = new JPanel(new BorderLayout());
    screen.setBackground(Color.WHITE);
    JPanel top = column();
    top.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
    add(top, label(date.format(new Date()) + "    演示模式 · 合成信号", 10, MUTED), 18, 8);
    JPanel heading = new JPanel(new BorderLayout(9, 0));
    heading.setOpaque(false);
    if (back) {
      JButton arrow = button("‹", false, this::home);
      arrow.setName("home");
      arrow.setFont(font(27, false));
      arrow.setBorder(BorderFactory.createEmptyBorder());
      arrow.setPreferredSize(new Dimension(20, 40));
      heading.add(arrow, BorderLayout.WEST);
    }
    JPanel titles = column();
    JLabel name = label(title, 18, INK);
    name.setFont(font(18, true));
    add(titles, name, 24, 4);
    add(titles, label(subtitle, 10, MUTED), 17, 0);
    heading.add(titles);
    add(top, heading, 46, 0);
    screen.add(top, BorderLayout.NORTH);
    body = scrollColumn();
    body.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    JScrollPane scroll = new JScrollPane(body);
    scroll.setBorder(null);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    scroll.getViewport().setBackground(Color.WHITE);
    screen.add(scroll);
    footer = column();
    footer.setBorder(BorderFactory.createEmptyBorder(9, 16, 14, 16));
    screen.add(footer, BorderLayout.SOUTH);
    setContentPane(screen);
  }

  void render() {
    screen.revalidate();
    screen.repaint();
  }

  void add(JPanel p, JComponent c, int height, int gap) {
    ReferenceTheme.add(p, c, height, gap);
  }

  void text(JPanel p, String value, int size, Color color) {
    add(p, wrappingLabel(value, size, color), 0, 10);
  }

  JPanel card() {
    JPanel p = panel();
    add(body, p, 0, 12);
    return p;
  }

  JButton control(String label, boolean primary, Runnable action) {
    return button(label, primary, () -> safe(action));
  }

  private void action(String value, Runnable action) {
    add(footer, control(value, true, action), 46, 0);
  }

  private void note(String value) {
    JLabel l = label(value, 10, MUTED);
    l.setHorizontalAlignment(SwingConstants.CENTER);
    add(footer, l, 20, 6);
  }

  private void info(String value) {
    JPanel p = card();
    p.setBackground(PALE);
    text(p, value, 11, BLUE);
  }

  private JTextField input(JPanel p, String value) {
    JTextField f = field(value);
    add(p, f, 40, 10);
    return f;
  }

  private JComboBox<DrugType> picker(JPanel p) {
    JComboBox<DrugType> c = new JComboBox<>(DrugType.values());
    c.setFont(font(12, false));
    c.setBackground(PALE);
    add(p, c, 40, 10);
    return c;
  }

  void safe(Runnable action) {
    try {
      action.run();
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(this, ex.getMessage(), "操作未完成", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void error(String message) {
    JOptionPane.showMessageDialog(this, message == null || message.isBlank() ? "操作失败，请重试" : message,
        "操作未完成", JOptionPane.WARNING_MESSAGE);
  }

  private String remainingText(DetectionSession session) {
    long ms = session.remainingMillis(), seconds = (ms + 999) / 1000;
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
  }

  private void cancel() {
    if (timer != null) {
      timer.stop();
      timer = null;
    }
    hideOverlay();
    if (session != null)
      session.cancel();
    session = null;
  }

  private void home() {
    cancel();
    page("毒品快速检测仪", "电化学检测终端", false);
    JPanel connection = card();
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.setOpaque(false);
    JLabel dot = label("●", 13, device.connected() ? GREEN : MUTED);
    row.add(dot, BorderLayout.WEST);
    JPanel summary = column();
    add(summary, label(device.connected() ? "模拟设备已连接，就绪" : "模拟设备未连接", 12, INK), 21, 1);
    add(summary, label("使用内置合成信号，不连接真实硬件", 10, MUTED), 16, 0);
    row.add(summary);
    row.add(label(device.connected() ? "已连接  ›" : "未连接  ›", 10, device.connected() ? GREEN : MUTED),
        BorderLayout.EAST);
    add(connection, row, 39, 0);
    connection.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    connection.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        devicePage();
      }
    });
    add(body, control(device.connected() ? "开始样品测试" : "连接设备", true,
        device.connected() ? this::newSample : this::devicePage), 52, 5);
    JLabel hint = label(device.connected() ? "活化 · 加样 · 反应 · 扫描 · 分析" : "连接后可开始样品测试", 10, MUTED);
    hint.setHorizontalAlignment(SwingConstants.CENTER);
    add(body, hint, 21, 16);
    JPanel shortcuts = new JPanel(new GridLayout(1, 3, 8, 0));
    shortcuts.setOpaque(false);
    String[] names = {"样品测试", "历史记录", "系统设置"}, icons = {"device", "records", "settings"};
    Runnable[] actions = {this::newSample, () -> history("", null), this::settings};
    for (int i = 0; i < names.length; i++) {
      JButton tile = control(names[i], false, actions[i]);
      tile.setIcon(new LineIcon(icons[i], 23, BLUE));
      tile.setVerticalTextPosition(SwingConstants.BOTTOM);
      tile.setHorizontalTextPosition(SwingConstants.CENTER);
      tile.setIconTextGap(9);
      tile.setFont(font(11, false));
      tile.setForeground(INK);
      if (i == 0)
        tile.setEnabled(device.connected());
      shortcuts.add(tile);
    }
    add(body, shortcuts, 87, 14);
    if (!device.connected())
      info("样品测试需先连接设备");
    int count = 0;
    try {
      String today = date.format(new Date()).substring(0, 10);
      for (DetectionResult r : repository.all())
        if (date.format(new Date(r.epochMillis)).startsWith(today))
          count++;
    } catch (IOException ignored) {
    }
    note(device.connected() ? "已就绪，可开始模拟样品测试" : "请先连接模拟设备");
    JPanel stats = new JPanel(new BorderLayout());
    stats.setOpaque(false);
    stats.add(label("今日已检测 " + count + " 例", 10, MUTED));
    stats.add(label(new SimpleDateFormat("yyyy-MM-dd").format(new Date()), 10, MUTED),
        BorderLayout.EAST);
    add(footer, stats, 18, 0);
    render();
  }

  private void devicePage() {
    page("设备连接", device == benchDevice ? "内置基准信号源" : "当前未发现可用设备", true);
    JPanel p = card();
    if (device.connected()) {
      text(p, "● 已连接设备", 16, GREEN);
      text(p, "设备通信正常\n可直接开始样品测试", 11, MUTED);
      add(body, control(device == benchDevice ? "断开基准信号源" : "断开连接", false, () -> {
        device.disconnect();
        device = realDevice;
        devicePage();
      }), 38, 12);
      info("✓ 设备就绪，可以开始样品测试。检测过程中请保持设备稳定。");
      action("开始样品测试", this::newSample);
    } else {
      text(p, "展示版使用内置模拟设备，无需连接真实硬件。", 11, MUTED);
      add(footer, control("重新连接内置模拟设备", true, () -> {
        device = benchDevice;
        benchDevice.connect();
        devicePage();
      }), 39, 8);
      add(footer, control("使用内置基准信号源", false, () -> {
        device = benchDevice;
        benchDevice.connect();
        devicePage();
      }), 20, 4);
    }
    if (device == benchDevice && !device.connected()) {
      device = realDevice;
    }
    render();
  }

  private void newSample() {
    if (!device.connected())
      throw new IllegalStateException("请先连接设备");
    if (unsaved != null) {
      savePending();
      return;
    }
    page("样品测试", device.connected() ? "设备已连接" : "设备未连接", true);
    add(body, new Steps(0), 65, 8);
    info("请准备进行样品测试。");
    JPanel p = card();
    text(p, "选择毒品类型", 12, MUTED);
    JComboBox<StandardCurve> drug = new JComboBox<>(library.all().toArray(new StandardCurve[0]));
    drug.setFont(font(13, false));
    add(p, drug, 40, 10);
    text(p, "样品编号", 12, MUTED);
    JTextField id = input(p, "");
    JButton demo = new JButton("加载内置测试样本");
    demo.setFont(font(11, false));
    demo.setToolTipText(DemoSample.standard().description());
    demo.addActionListener(e -> {
      DemoSample sample = DemoSample.standard();
      id.setText(sample.sampleId());
      drug.setSelectedItem(library.all().stream().filter(c -> c.drug == sample.drug()).findFirst().orElse(null));
    });
    add(p, demo, 34, 4);
    text(p, "样品编号将保存到检测记录。内置样本仅生成合成信号。", 11, MUTED);
    action("开始活化", () -> {
      if (!device.connected())
        throw new IllegalStateException("请先连接设备");
      StandardCurve chosen = (StandardCurve) drug.getSelectedItem();
      if (chosen == null)
        throw new IllegalStateException("请先由管理员保存一条标准曲线");
      selectedCalibration = chosen.fit();
      selectedOrigin = chosen.origin;
      session = new DetectionSession(id.getText().trim(), chosen.drug,
          minutes * 60000L, () -> System.nanoTime() / 1000000);
      step();
      try {
        device.baseline();
      } catch (RuntimeException ex) {
        error(ex.getMessage());
        return;
      }
      session.confirmElectrode();
      step();
    });
    render();
  }

  private void step() {
    page("样品测试", session.drug.label + "  ·  " + session.sampleId, true);
    int index = Math.min(4, session.stage().ordinal());
    add(body, new Steps(index), 65, 8);
    String[] hints = {"请插入电极试条，滴加电解质后开始活化。", "活化已完成，请滴加 10 μL 待测样品。", "保持设备静止，等待样品充分反应。",
        "反应已完成，请冲洗电极并添加反应液。", "正在分析样品，请勿关闭设备。"};
    info((index + 1) + "  " + hints[index]);
    JPanel p = card();
    text(p, "当前样品", 10, MUTED);
    text(p, session.drug.label, 16, INK);
    text(p, "样品编号   " + session.sampleId, 11, MUTED);
    switch (session.stage()) {
      case PREPARED :
        illustration("device", "电极准备", "确认电极试条已插入检测口，并保持稳定。");
        action("开始活化", () -> {
          try {
            device.baseline();
          } catch (RuntimeException ex) {
            error(ex.getMessage());
            return;
          }
          session.confirmElectrode();
          step();
        });
        break;
      case SAMPLE_ADDED :
        illustration("drop", "滴加样品", "将样品滴入试条电极区域，确认完成后开始反应计时。");
        action("已加样，开始反应计时", () -> {
          session.confirmSample();
          step();
        });
        break;
      case REACTING :
        illustration("clock", "等待反应",
            "常规反应时间：" + minutes + " 分钟。");
        JLabel left = label(remainingText(session), 32, BLUE);
        left.setHorizontalAlignment(SwingConstants.CENTER);
        add(body, left, 55, 10);
        JButton next = control("计时结束后继续", true, () -> {
          timer.stop();
          session.finishReaction();
          step();
        });
        next.setEnabled(false);
        add(footer, next, 46, 0);
        add(footer, control("跳过计时（展示模式）", false, () -> {
          if (timer != null)
            timer.stop();
          session.skipReaction();
          step();
        }), 39, 0);
        timer = new javax.swing.Timer(100, e -> {
          long ms = session.remainingMillis(), seconds = (ms + 999) / 1000;
          left.setText(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
          next.setEnabled(ms == 0);
          if (ms == 0)
            timer.stop();
        });
        timer.start();
        break;
      case READY :
        illustration("drop", "冲洗并添加反应液", "用清洁液冲洗电极，去除多余液体，再添加 50 μL 反应液。");
        action("开始检测", this::measure);
        break;
      default :
        break;
    }
    note("常规计时 · 反应阶段请保持设备稳定");
    render();
  }

  private void illustration(String icon, String title, String message) {
    JPanel p = column();
    p.setBorder(BorderFactory.createEmptyBorder(16, 8, 8, 8));
    JLabel image = new JLabel(new LineIcon(icon, 44, MUTED));
    image.setHorizontalAlignment(SwingConstants.CENTER);
    add(p, image, 49, 8);
    JLabel name = label(title, 16, INK);
    name.setHorizontalAlignment(SwingConstants.CENTER);
    add(p, name, 28, 9);
    JLabel text = label(message, 11, MUTED);
    text.setHorizontalAlignment(SwingConstants.CENTER);
    add(p, text, 42, 0);
    add(body, p, 0, 10);
  }

  void showOverlay(JPanel content) {
    JPanel shade = new JPanel(new GridBagLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        g.setColor(new Color(22, 40, 66, 100));
        g.fillRect(0, 0, getWidth(), getHeight());
      }
    };
    shade.setOpaque(false);
    // 遮罩显式接收鼠标事件，防止采集或输入弹窗期间点击到下层页面。
    shade.addMouseListener(new java.awt.event.MouseAdapter() {
    });
    shade.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
    });
    shade.setFocusable(true);
    shade.setFocusCycleRoot(true);
    shade.add(content);
    setGlassPane(shade);
    shade.setVisible(true);
    shade.requestFocusInWindow();
    shade.revalidate();
  }

  void hideOverlay() {
    if (getGlassPane() != null)
      getGlassPane().setVisible(false);
  }

  void progress(String title, String message, int millis, Runnable complete) {
    JPanel p = panel();
    p.setPreferredSize(new Dimension(310, 245));
    LoadingRing spinner = new LoadingRing();
    add(p, spinner, 32, 5);
    JLabel name = label(title, 16, INK);
    name.setHorizontalAlignment(SwingConstants.CENTER);
    name.setFont(font(16, true));
    add(p, name, 33, 10);
    JLabel detail = label(message, 12, MUTED);
    detail.setHorizontalAlignment(SwingConstants.CENTER);
    add(p, detail, 30, 15);
    JProgressBar bar = new JProgressBar(0, 100);
    bar.setForeground(BLUE);
    bar.setBackground(PALE);
    bar.setBorderPainted(false);
    add(p, bar, 6, 15);
    JLabel caption = label("数据采集中 · 请勿关闭应用", 10, MUTED);
    caption.setHorizontalAlignment(SwingConstants.CENTER);
    add(p, caption, 18, 0);
    showOverlay(p);
    long start = System.nanoTime() / 1000000;
    timer = new javax.swing.Timer(40, e -> {
      long elapsed = System.nanoTime() / 1000000 - start;
      spinner.repaint();
      bar.setValue((int) Math.min(100, elapsed * 100 / millis));
      if (elapsed >= millis) {
        timer.stop();
        hideOverlay();
        safe(complete);
      }
    });
    timer.start();
  }

  private void measure() {
    session.beginMeasurement(device.connected());
    step();
    progress("正在检测", "正在扫描并分析样品信号…", 1600, () -> {
      unsaved = session.complete(selectedCalibration, device.baseline(),
          device.sample(session.sampleId, session.drug), System.currentTimeMillis(),
          DataOrigin.combine(device.origin(), selectedOrigin));
      savePending();
    });
  }

  private void savePending() {
    try {
      repository.save(unsaved);
      DetectionResult saved = unsaved;
      unsaved = null;
      detail(saved, 0);
    } catch (IOException ex) {
      page("结果尚未保存", "请重试保存", true);
      info(ex.getMessage() + "；当前结果仅在内存中。");
      action("重试保存", this::savePending);
      render();
    }
  }

  private void tabs(String[] names, int selected, java.util.function.IntConsumer action) {
    JPanel row = new JPanel(new GridLayout(1, names.length, 0, 0));
    row.setOpaque(false);
    for (int i = 0; i < names.length; i++) {
      final int n = i;
      JButton b = control(names[i], false, () -> action.accept(n));
      b.setForeground(i == selected ? BLUE : MUTED);
      if (i == selected)
        b.setFont(font(12, true));
      row.add(b);
    }
    add(body, row, 36, 14);
  }

  private void detail(DetectionResult r, int tab) {
    page("记录详情", r.sampleId + "  ·  " + r.drug.label, true);
    tabs(new String[]{"检测信息", "DPV 曲线", "计算详情"}, tab, i -> detail(r, i));
    JPanel p = card();
    if (tab == 0) {
      text(p, r.origin == DataOrigin.SIMULATED ? "模拟检测完成" : "检测完成", 21, GREEN);
      text(p, String.format(Locale.ROOT, "%.3f ng/L", r.concentration), 28, INK);
      text(p, r.status(), 11, MUTED);
      text(p, "毒品类型    " + r.drug.label + "\n样品编号    " + r.sampleId + "\n检测时间    "
          + date.format(new Date(r.epochMillis)), 12, INK);
      text(p, "✓ 已保存至本地记录", 11, GREEN);
    } else if (tab == 1) {
      text(p, "DPV 信号", 13, INK);
      add(p, new Plot(r.signal, "V", "DPV 信号"), 235, 0);
    } else {
      text(p, "峰电流计算", 14, INK);
      text(p, String.format(Locale.ROOT, "基线峰 I0    %.5f μA\n样品峰 I1    %.5f μA\n峰电流差 ΔI    %.5f μA",
          r.baselinePeak, r.samplePeak, r.samplePeak - r.baselinePeak), 12, INK);
      text(p, r.calibration.equation(), 12, BLUE);
      text(p, "适用区间 " + r.calibration.min + "–" + r.calibration.max + " ng/L\n标曲 ID："
          + r.calibration.id, 10, MUTED);
    }
    note("结果保存于本地记录 · 可导出存档");
    action("导出检测数据", () -> export(Collections.singletonList(r)));
    add(footer, control("历史记录", false, () -> history("", null)), 38, 0);
    render();
  }

  private void history(String query, DrugType filter) {
    page("历史记录", "检测结果查询与导出", true);
    Set<String> selected = new HashSet<>();
    JPanel controls = card();
    JTextField search = input(controls, query);
    search.setToolTipText("搜索样品编号");
    JComboBox<Object> drug = new JComboBox<>();
    drug.addItem("全部类型");
    for (DrugType d : DrugType.values())
      drug.addItem(d);
    if (filter != null)
      drug.setSelectedItem(filter);
    drug.setFont(font(12, false));
    add(controls, drug, 35, 9);
    add(controls,
        control("查询", false, () -> history(search.getText().trim(),
            drug.getSelectedItem() instanceof DrugType ? (DrugType) drug.getSelectedItem() : null)),
        35, 0);
    try {
      List<DetectionResult> rows = repository.search(query, filter);
      text(body, "共 " + rows.size() + " 条记录", 11, MUTED);
      if (rows.isEmpty())
        illustration("records", "暂无检测记录", "完成一次样品测试后，结果将显示在这里。");
      for (DetectionResult r : rows) {
        JPanel item = card();
        JCheckBox check = new JCheckBox(r.sampleId);
        check.setOpaque(false);
        check.setFont(font(13, true));
        check.setForeground(INK);
        check.addActionListener(e -> {
          if (check.isSelected())
            selected.add(r.id);
          else
            selected.remove(r.id);
        });
        add(item, check, 25, 5);
        text(item,
            r.drug.label + "  ·  " + String.format(Locale.ROOT, "%.3f ng/L", r.concentration), 12,
            INK);
        text(item, date.format(new Date(r.epochMillis)), 10, MUTED);
        JPanel options = new JPanel(new GridLayout(1, 2, 8, 0));
        options.setOpaque(false);
        options.add(control("查看详情", false, () -> detail(r, 0)));
        options.add(control("导出", false, () -> export(Collections.singletonList(r))));
        add(item, options, 34, 0);
      }
      action("导出所选记录", () -> {
        List<DetectionResult> chosen = new ArrayList<>();
        for (DetectionResult r : rows)
          if (selected.contains(r.id))
            chosen.add(r);
        export(chosen);
      });
    } catch (IOException ex) {
      info("读取失败：" + ex.getMessage());
    }
    render();
  }

  private void export(List<DetectionResult> rows) {
    if (rows.isEmpty())
      throw new IllegalArgumentException("请先勾选至少一条记录");
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new File("检测记录.xlsx"));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
      return;
    Path path = chooser.getSelectedFile().toPath();
    if (!path.toString().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
      path = Paths.get(path + ".xlsx");
    if (Files.exists(path) && JOptionPane.showConfirmDialog(this, "文件已存在，是否覆盖？", "导出",
        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
      return;
    try (OutputStream out = Files.newOutputStream(path)) {
      XlsxExporter.write(rows, out);
      JOptionPane.showMessageDialog(this, "检测数据已导出");
    } catch (IOException ex) {
      throw new IllegalStateException("导出失败：" + ex.getMessage(), ex);
    }
  }

  private void authorization() {
    JPanel p = panel();
    p.setPreferredSize(new Dimension(315, 405));
    JLabel title = label("管理员授权", 17, INK);
    title.setHorizontalAlignment(SwingConstants.CENTER);
    title.setFont(font(17, true));
    add(p, title, 30, 4);
    JLabel sub = label("请输入管理员口令", 11, MUTED);
    sub.setHorizontalAlignment(SwingConstants.CENTER);
    add(p, sub, 23, 9);
    JLabel dots = label("○   ○   ○   ○   ○   ○", 22, BLUE);
    dots.setHorizontalAlignment(SwingConstants.CENTER);
    add(p, dots, 40, 12);
    StringBuilder code = new StringBuilder();
    JPanel keys = new JPanel(new GridLayout(4, 4, 7, 7));
    keys.setOpaque(false);
    for (String key : new String[]{"1", "2", "3", "删除", "4", "5", "6", "清除", "7", "8", "9", "取消",
        "", "0", "", "确认"})
      keys.add(control(key, key.equals("确认"), () -> {
        if (key.equals("取消")) {
          hideOverlay();
          return;
        }
        if (key.equals("清除")) {
          code.setLength(0);
        } else if (key.equals("删除")) {
          if (code.length() > 0)
            code.deleteCharAt(code.length() - 1);
        } else if (key.equals("确认")) {
          if (code.length() == 6) {
            hideOverlay();
            administrator = true;
            settings();
            return;
          }
          code.setLength(0);
          sub.setText("口令不匹配，请重新输入");
        } else if (!key.isEmpty() && code.length() < 6)
          code.append(key);
        StringBuilder mask = new StringBuilder();
        for (int i = 0; i < 6; i++)
          mask.append(i < code.length() ? "●   " : "○   ");
        dots.setText(mask.toString().trim());
      }));
    add(p, keys, 191, 10);
    JLabel hint = label("管理员口令由运维在部署时配置", 10, MUTED);
    hint.setHorizontalAlignment(SwingConstants.CENTER);
    add(p, hint, 22, 0);
    JButton close = control("取消", false, this::hideOverlay);
    add(p, close, 28, 0);
    showOverlay(p);
  }

  /** 列表采用单选状态；修改使用库返回的副本，只有点击结果保存才写回。 */
  void calibrationManager() {
    if (!administrator)
      throw new IllegalStateException("请先开启管理员模式");
    page("标准曲线管理", "共 " + library.all().size() + " 条曲线", true);
    StandardCurve[] selected = {null};
    ButtonGroup group = new ButtonGroup();
    text(body, "名称                                  来源", 11, MUTED);
    for (StandardCurve curve : library.all()) {
      JRadioButton row = new JRadioButton(curve.name + "                             "
          + "本地标定");
      row.setFont(font(13, false));
      row.setOpaque(false);
      row.setForeground(INK);
      row.addActionListener(e -> selected[0] = curve);
      group.add(row);
      add(body, row, 46, 2);
    }
    note("已保存的标曲可在样品测试中选择");
    JPanel actions = new JPanel(new GridLayout(1, 3, 8, 0));
    actions.setOpaque(false);
    actions
        .add(control("添加", true, () -> new DesktopCurveEditor(this, new StandardCurve()).start()));
    actions.add(control("修改", false, () -> {
      if (selected[0] == null)
        throw new IllegalStateException("请先选中一条标曲");
      new DesktopCurveEditor(this, selected[0]).start();
    }));
    actions.add(control("删除", false, () -> {
      if (selected[0] == null)
        throw new IllegalStateException("请先选中一条标曲");
      new DesktopCurveEditor(this, selected[0]).confirm("删除标准曲线",
          "确认删除标准曲线「" + selected[0].name + "」？\n删除后不可恢复，已保存的检测记录不受影响。", () -> {
            try {
              library.delete(selected[0].id);
              calibrationManager();
            } catch (IOException ex) {
              throw new IllegalStateException("删除失败", ex);
            }
          });
    }));
    add(footer, actions, 46, 0);
    render();
  }

  private void settings() {
    page("系统设置", "设备与检测参数", true);
    JPanel p = card();
    text(p, "管理员模式", 14, INK);
    text(p, "开启后可修改检测参数、维护标准曲线等功能。", 11, MUTED);
    JButton enable = control(administrator ? "管理员模式已开启" : "开启管理员模式", !administrator,
        this::authorization);
    enable.setEnabled(!administrator);
    add(p, enable, 43, 8);
    JPanel maintenance = card();
    text(maintenance, "维护入口", 12, INK);
    JButton manage = control("标准曲线管理", false, this::calibrationManager);
    manage.setEnabled(administrator);
    JPanel entries = new JPanel(new GridLayout(1, 2, 8, 0));
    entries.setOpaque(false);
    entries.add(manage);
    entries.add(control("调整时间", false, () -> JOptionPane.showMessageDialog(this,
        "当前时间：" + date.format(new Date()) + "\n应用跟随电脑系统时间，请在系统设置中调整。")));
    add(maintenance, entries, 43, 8);
    if (administrator)
      add(maintenance, control("诊断", true, this::diagnostics), 43, 8);
    JPanel system = card();
    text(system, "系统信息", 12, MUTED);
    text(system, "当前时间    " + date.format(new Date()), 11, INK);
    text(system, "终端电量    电脑端不提供", 11, INK);
    text(system, "版本    1.0.0", 11, INK);
    note("时间跟随电脑系统");
    render();
  }

  /** 运行状态与检测参数收纳到诊断入口，保持系统设置主页面与参考图一致。 */
  private void diagnostics() {
    JPanel p = column();
    text(p, "工作站状态：" + (device.connected() ? "已连接" : "未连接") + "\n标曲数量：" + library.all().size(), 12,
        INK);
    JComboBox<Integer> duration = new JComboBox<>(new Integer[]{5, 10});
    duration.setSelectedItem(minutes);
    p.add(duration);
    if (JOptionPane.showConfirmDialog(this, p, "诊断与运行参数",
        JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
      minutes = (Integer) duration.getSelectedItem();
    }
  }

  @Override
  public void dispose() {
    cancel();
    device.disconnect();
    super.dispose();
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        new DesktopApp(Paths.get(System.getProperty("detector.data", "data"))).setVisible(true);
      } catch (Exception ex) {
        JOptionPane.showMessageDialog(null, ex.getMessage());
      }
    });
  }

  static final class Plot extends JComponent {
    private final Signal signal;
    private final String unit, title;

    Plot(Signal signal, String unit, String title) {
      this.signal = signal;
      this.unit = unit;
      this.title = title;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      double[] x = signal.voltages(), y = signal.currents();
      double min = Arrays.stream(y).min().orElse(0), max = Arrays.stream(y).max().orElse(1),
          span = Math.max(0.1, max - min);
      int left = 43, right = getWidth() - 15, top = 28, bottom = getHeight() - 35;
      g.setFont(font(9, false));
      g.setColor(MUTED);
      g.drawString("μA", 4, 13);
      g.drawString(title, left, 13);
      for (int i = 0; i < 5; i++) {
        int yy = top + (bottom - top) * i / 4;
        g.setColor(LINE);
        g.drawLine(left, yy, right, yy);
        g.setColor(MUTED);
        g.drawString(String.format(Locale.ROOT, "%.1f", max - span * i / 4), 3, yy + 3);
      }
      Path2D line = new Path2D.Double();
      for (int i = 0; i < x.length; i++) {
        double xx = left + (x[i] - x[0]) / (x[x.length - 1] - x[0]) * (right - left),
            yy = bottom - (y[i] - min) / span * (bottom - top);
        if (i == 0)
          line.moveTo(xx, yy);
        else
          line.lineTo(xx, yy);
      }
      g.setColor(BLUE);
      g.setStroke(new BasicStroke(1.7f));
      g.draw(line);
      g.setColor(MUTED);
      g.drawString(String.format(Locale.ROOT, "%.1f", x[0]), left, bottom + 17);
      g.drawString(String.format(Locale.ROOT, "%.1f", x[x.length - 1]), right - 30, bottom + 17);
      g.drawString(unit, (left + right) / 2, bottom + 30);
      g.dispose();
    }
  }
}
