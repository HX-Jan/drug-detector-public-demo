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

/** Portrait desktop counterpart of the reference-led native Android UI. */
public final class DesktopApp extends JFrame {
  private final Workstation device = new SimulatedWorkstation();
  private final ResultRepository repository;
  private final EnumMap<DrugType, Calibration> curves = new EnumMap<>(DrugType.class);
  private JPanel body, footer, screen;
  private javax.swing.Timer timer;
  private DetectionSession session;
  private DetectionResult unsaved;
  private boolean accelerated = true;
  private int minutes = 5;
  private final SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  public DesktopApp(Path data) throws IOException {
    super("毒品快速检测仪 · 公开演示版");
    repository = new FileResultRepository(data);
    for (DrugType d : DrugType.values()) curves.put(d, Calibration.demo(d));
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(440, 780);
    setMinimumSize(new Dimension(380, 660));
    setLocationRelativeTo(null);
    home();
  }

  private void page(String title, String subtitle, boolean back) {
    hideOverlay();
    screen = new JPanel(new BorderLayout());
    screen.setBackground(Color.WHITE);
    JPanel top = column();
    top.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
    add(top, label(date.format(new Date()), 10, MUTED), 18, 8);
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

  private void render() {
    screen.revalidate();
    screen.repaint();
  }

  private void add(JPanel p, JComponent c, int height, int gap) {
    ReferenceTheme.add(p, c, height, gap);
  }

  private void text(JPanel p, String value, int size, Color color) {
    add(p, label(value, size, color), 0, 10);
  }

  private JPanel card() {
    JPanel p = panel();
    add(body, p, 0, 12);
    return p;
  }

  private JButton control(String label, boolean primary, Runnable action) {
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

  private void safe(Runnable action) {
    try {
      action.run();
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(this, ex.getMessage(), "操作未完成", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void cancel() {
    if (timer != null) {
      timer.stop();
      timer = null;
    }
    hideOverlay();
    if (session != null) session.cancel();
    session = null;
  }

  private void home() {
    cancel();
    page("毒品快速检测仪", "电化学检测终端  ·  公开演示", false);
    JPanel connection = card();
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.setOpaque(false);
    JLabel dot = label("●", 13, device.connected() ? GREEN : MUTED);
    row.add(dot, BorderLayout.WEST);
    JPanel summary = column();
    add(summary, label(device.connected() ? "设备已连接，就绪" : "设备未连接", 12, INK), 21, 1);
    add(summary, label("模拟工作站  DEMO-P20", 10, MUTED), 16, 0);
    row.add(summary);
    row.add(
        label(device.connected() ? "已连接  ›" : "未连接  ›", 10, device.connected() ? GREEN : MUTED),
        BorderLayout.EAST);
    add(connection, row, 39, 0);
    connection.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    connection.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            devicePage();
          }
        });
    add(
        body,
        control(
            device.connected() ? "开始样品测试" : "连接设备",
            true,
            device.connected() ? this::newSample : this::devicePage),
        52,
        5);
    JLabel hint = label(device.connected() ? "活化 · 加样 · 反应 · 扫描 · 分析" : "连接后可开始样品测试", 10, MUTED);
    hint.setHorizontalAlignment(SwingConstants.CENTER);
    add(body, hint, 21, 16);
    JPanel shortcuts = new JPanel(new GridLayout(1, 3, 8, 0));
    shortcuts.setOpaque(false);
    String[] names = {"设备连接", "历史记录", "系统设置"}, icons = {"bluetooth", "records", "settings"};
    Runnable[] actions = {this::devicePage, () -> history("", null), this::settings};
    for (int i = 0; i < names.length; i++) {
      JButton tile = control(names[i], false, actions[i]);
      tile.setIcon(new LineIcon(icons[i], 23, BLUE));
      tile.setVerticalTextPosition(SwingConstants.BOTTOM);
      tile.setHorizontalTextPosition(SwingConstants.CENTER);
      tile.setIconTextGap(9);
      tile.setFont(font(11, false));
      tile.setForeground(INK);
      shortcuts.add(tile);
    }
    add(body, shortcuts, 87, 14);
    if (!device.connected()) info("样品测试需先连接设备");
    int count = 0;
    try {
      String today = date.format(new Date()).substring(0, 10);
      for (DetectionResult r : repository.all())
        if (date.format(new Date(r.epochMillis)).startsWith(today)) count++;
    } catch (IOException ignored) {
    }
    note("演示模式 · 所有检测信号均为合成数据");
    JPanel stats = new JPanel(new BorderLayout());
    stats.setOpaque(false);
    stats.add(label("今日已检测 " + count + " 例", 10, MUTED));
    stats.add(
        label(new SimpleDateFormat("yyyy-MM-dd").format(new Date()), 10, MUTED), BorderLayout.EAST);
    add(footer, stats, 18, 0);
    render();
  }

  private void devicePage() {
    page("设备连接", "模拟工作站连接", true);
    JPanel p = card();
    text(p, "设备连接状态", 14, INK);
    text(
        p,
        device.connected() ? "● 已连接  DEMO-P20" : "○ 未连接设备",
        13,
        device.connected() ? GREEN : MUTED);
    text(p, "此设备为演示连接，不扫描真实蓝牙或 USB。", 11, MUTED);
    note("模拟设备  DEMO-P20");
    if (device.connected())
      action(
          "断开设备",
          () -> {
            device.disconnect();
            devicePage();
          });
    else
      action(
          "连接模拟设备",
          () ->
              progress(
                  "正在连接",
                  "正在连接 DEMO-P20…",
                  1000,
                  () -> {
                    device.connect();
                    home();
                  }));
    render();
  }

  private void newSample() {
    if (unsaved != null) {
      savePending();
      return;
    }
    page("样品测试", "登记样品信息", true);
    JPanel p = card();
    text(p, "选择毒品类型", 12, MUTED);
    JComboBox<DrugType> drug = picker(p);
    text(p, "样品编号", 12, MUTED);
    JTextField id = input(p, "DEMO-" + System.currentTimeMillis() % 1000000);
    text(p, "仅使用虚构编号，结果单位为 ng/L。", 11, MUTED);
    action(
        "开始测试",
        () -> {
          if (!device.connected()) throw new IllegalStateException("请先连接设备");
          session =
              new DetectionSession(
                  id.getText().trim(),
                  (DrugType) drug.getSelectedItem(),
                  accelerated ? 5000 : minutes * 60000L,
                  () -> System.nanoTime() / 1000000);
          step();
        });
    render();
  }

  private void step() {
    page("样品测试", session.drug.label + "  ·  " + session.sampleId, true);
    int index = Math.min(4, session.stage().ordinal());
    add(body, new Steps(index), 65, 8);
    String[] hints = {
      "请插入电极试条，滴加电解质后开始活化。",
      "活化已完成，请滴加 10 μL 待测样品。",
      "保持设备静止，等待样品充分反应。",
      "反应已完成，请冲洗电极并添加反应液。",
      "正在分析样品，请勿关闭设备。"
    };
    info((index + 1) + "  " + hints[index]);
    JPanel p = card();
    text(p, "当前样品", 10, MUTED);
    text(p, session.drug.label, 16, INK);
    text(p, "样品编号   " + session.sampleId, 11, MUTED);
    switch (session.stage()) {
      case PREPARED:
        illustration("device", "电极准备", "确认电极试条已插入检测口，并保持稳定。");
        action(
            "开始活化",
            () ->
                progress(
                    "电极活化中",
                    "正在执行模拟活化，请等待…",
                    1400,
                    () -> {
                      session.confirmElectrode();
                      step();
                    }));
        break;
      case SAMPLE_ADDED:
        illustration("drop", "滴加样品", "将样品滴入试条电极区域，确认完成后开始反应计时。");
        action(
            "已加样，开始反应计时",
            () -> {
              session.confirmSample();
              step();
            });
        break;
      case REACTING:
        illustration(
            "clock",
            "等待反应",
            accelerated ? "演示加速：5 秒代表实际 " + minutes + " 分钟。" : "常规反应时间：" + minutes + " 分钟。");
        JLabel left = label("00:05", 32, BLUE);
        left.setHorizontalAlignment(SwingConstants.CENTER);
        add(body, left, 55, 10);
        JButton next =
            control(
                "计时结束后继续",
                true,
                () -> {
                  timer.stop();
                  session.finishReaction();
                  step();
                });
        next.setEnabled(false);
        add(footer, next, 46, 0);
        timer =
            new javax.swing.Timer(
                100,
                e -> {
                  long ms = session.remainingMillis(), seconds = (ms + 999) / 1000;
                  left.setText(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
                  next.setEnabled(ms == 0);
                  if (ms == 0) timer.stop();
                });
        timer.start();
        break;
      case READY:
        illustration("drop", "冲洗并添加反应液", "用清洁液冲洗电极，去除多余液体，再添加 50 μL 反应液。");
        action("开始检测", this::measure);
        break;
      default:
        break;
    }
    note("演示模式 · " + (accelerated ? "加速计时" : "常规计时"));
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

  private void showOverlay(JPanel content) {
    JPanel shade =
        new JPanel(new GridBagLayout()) {
          @Override
          protected void paintComponent(Graphics g) {
            g.setColor(new Color(22, 40, 66, 100));
            g.fillRect(0, 0, getWidth(), getHeight());
          }
        };
    shade.setOpaque(false);
    shade.setFocusable(true);
    shade.setFocusCycleRoot(true);
    shade.add(content);
    setGlassPane(shade);
    shade.setVisible(true);
    shade.requestFocusInWindow();
    shade.revalidate();
  }

  private void hideOverlay() {
    if (getGlassPane() != null) getGlassPane().setVisible(false);
  }

  private void progress(String title, String message, int millis, Runnable complete) {
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
    JLabel caption = label("模拟采集 · 请勿关闭应用", 10, MUTED);
    caption.setHorizontalAlignment(SwingConstants.CENTER);
    add(p, caption, 18, 0);
    showOverlay(p);
    long start = System.nanoTime() / 1000000;
    timer =
        new javax.swing.Timer(
            40,
            e -> {
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
    progress(
        "正在检测",
        "正在扫描并分析样品信号…",
        1600,
        () -> {
          unsaved =
              session.complete(
                  curves.get(session.drug),
                  device.baseline(),
                  device.sample(session.sampleId, session.drug),
                  System.currentTimeMillis());
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
      if (i == selected) b.setFont(font(12, true));
      row.add(b);
    }
    add(body, row, 36, 14);
  }

  private void detail(DetectionResult r, int tab) {
    page("记录详情", r.sampleId + "  ·  " + r.drug.label, true);
    tabs(new String[] {"检测信息", "DPV 曲线", "计算详情"}, tab, i -> detail(r, i));
    JPanel p = card();
    if (tab == 0) {
      text(p, "模拟检测完成", 21, GREEN);
      text(p, String.format(Locale.ROOT, "%.3f ng/L", r.concentration), 28, INK);
      text(p, r.status(), 11, MUTED);
      text(
          p,
          "毒品类型    "
              + r.drug.label
              + "\n样品编号    "
              + r.sampleId
              + "\n检测时间    "
              + date.format(new Date(r.epochMillis)),
          12,
          INK);
      text(p, "✓ 已保存至本地记录", 11, GREEN);
    } else if (tab == 1) {
      text(p, "合成 DPV 信号", 13, INK);
      add(p, new Plot(r.signal, "V", "合成 DPV 信号"), 235, 0);
    } else {
      text(p, "峰电流计算", 14, INK);
      text(
          p,
          String.format(
              Locale.ROOT,
              "基线峰 I0    %.5f μA\n样品峰 I1    %.5f μA\n峰电流差 ΔI    %.5f μA",
              r.baselinePeak,
              r.samplePeak,
              r.samplePeak - r.baselinePeak),
          12,
          INK);
      text(p, r.calibration.equation(), 12, BLUE);
      text(
          p,
          "适用区间 "
              + r.calibration.min
              + "–"
              + r.calibration.max
              + " ng/L\n标曲 ID："
              + r.calibration.id,
          10,
          MUTED);
    }
    note("合成数据 · 不作为真实检测结论");
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
    for (DrugType d : DrugType.values()) drug.addItem(d);
    if (filter != null) drug.setSelectedItem(filter);
    drug.setFont(font(12, false));
    add(controls, drug, 35, 9);
    add(
        controls,
        control(
            "查询",
            false,
            () ->
                history(
                    search.getText().trim(),
                    drug.getSelectedItem() instanceof DrugType
                        ? (DrugType) drug.getSelectedItem()
                        : null)),
        35,
        0);
    try {
      List<DetectionResult> rows = repository.search(query, filter);
      text(body, "共 " + rows.size() + " 条记录  ·  合成数据", 11, MUTED);
      if (rows.isEmpty()) illustration("records", "暂无检测记录", "完成一次样品测试后，结果将显示在这里。");
      for (DetectionResult r : rows) {
        JPanel item = card();
        JCheckBox check = new JCheckBox(r.sampleId);
        check.setOpaque(false);
        check.setFont(font(13, true));
        check.setForeground(INK);
        check.addActionListener(
            e -> {
              if (check.isSelected()) selected.add(r.id);
              else selected.remove(r.id);
            });
        add(item, check, 25, 5);
        text(
            item,
            r.drug.label + "  ·  " + String.format(Locale.ROOT, "%.3f ng/L", r.concentration),
            12,
            INK);
        text(item, date.format(new Date(r.epochMillis)), 10, MUTED);
        JPanel options = new JPanel(new GridLayout(1, 2, 8, 0));
        options.setOpaque(false);
        options.add(control("查看详情", false, () -> detail(r, 0)));
        options.add(control("导出", false, () -> export(Collections.singletonList(r))));
        add(item, options, 34, 0);
      }
      action(
          "导出所选记录",
          () -> {
            List<DetectionResult> chosen = new ArrayList<>();
            for (DetectionResult r : rows) if (selected.contains(r.id)) chosen.add(r);
            export(chosen);
          });
    } catch (IOException ex) {
      info("读取失败：" + ex.getMessage());
    }
    render();
  }

  private void export(List<DetectionResult> rows) {
    if (rows.isEmpty()) throw new IllegalArgumentException("请先勾选至少一条记录");
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new File("SIMULATED-results.xlsx"));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    Path path = chooser.getSelectedFile().toPath();
    if (!path.toString().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
      path = Paths.get(path + ".xlsx");
    if (Files.exists(path)
        && JOptionPane.showConfirmDialog(this, "文件已存在，是否覆盖？", "导出", JOptionPane.YES_NO_OPTION)
            != JOptionPane.YES_OPTION) return;
    try (OutputStream out = Files.newOutputStream(path)) {
      XlsxExporter.write(rows, out);
      JOptionPane.showMessageDialog(this, "检测数据已导出（模拟）");
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
    JLabel sub = label("请输入演示口令", 11, MUTED);
    sub.setHorizontalAlignment(SwingConstants.CENTER);
    add(p, sub, 23, 9);
    JLabel dots = label("○   ○   ○   ○   ○   ○", 22, BLUE);
    dots.setHorizontalAlignment(SwingConstants.CENTER);
    add(p, dots, 40, 12);
    StringBuilder code = new StringBuilder();
    JPanel keys = new JPanel(new GridLayout(4, 3, 7, 7));
    keys.setOpaque(false);
    for (String key : new String[] {"1", "2", "3", "4", "5", "6", "7", "8", "9", "删除", "0", "确认"})
      keys.add(
          control(
              key,
              key.equals("确认"),
              () -> {
                if (key.equals("删除")) {
                  if (code.length() > 0) code.deleteCharAt(code.length() - 1);
                } else if (key.equals("确认")) {
                  if (code.toString().equals("123456")) {
                    hideOverlay();
                    calibrationManager();
                    return;
                  }
                  code.setLength(0);
                  sub.setText("口令不匹配，请输入 123456");
                } else if (code.length() < 6) code.append(key);
                StringBuilder mask = new StringBuilder();
                for (int i = 0; i < 6; i++) mask.append(i < code.length() ? "●   " : "○   ");
                dots.setText(mask.toString().trim());
              }));
    add(p, keys, 191, 10);
    JLabel hint = label("演示口令：123456，仅用于界面展示", 10, MUTED);
    hint.setHorizontalAlignment(SwingConstants.CENTER);
    add(p, hint, 22, 0);
    JButton close = control("取消", false, this::hideOverlay);
    add(p, close, 28, 0);
    showOverlay(p);
  }

  private void calibrationManager() {
    page("标准曲线管理", "内置演示参数", true);
    for (DrugType drug : DrugType.values()) {
      JPanel p = card();
      text(p, drug.label, 14, INK);
      text(
          p, "来源：" + (curves.get(drug).id.startsWith("DEMO-FIXTURE") ? "内置演示" : "本地拟合"), 11, MUTED);
      add(p, control("查看 / 绘制标曲 · " + drug.label, false, () -> calibration(drug)), 35, 0);
    }
    note("标曲为合成样例，不代表仪器性能");
    render();
  }

  private void calibration(DrugType drug) {
    page("标准曲线", drug.label + "  ·  演示标定", true);
    JPanel p = card();
    text(p, curves.get(drug).equation(), 11, BLUE);
    add(p, new Plot(curves.get(drug).fittedLine(), "ng/L", "标准曲线"), 180, 0);
    JPanel inputs = card();
    text(inputs, "浓度（ng/L，5–10 个递增值）", 11, MUTED);
    JTextField x = input(inputs, "1,10,25,50,100");
    text(inputs, "峰电流 I0 到 In（μA）", 11, MUTED);
    JTextField peaks = input(inputs, "1,1.28,2.28,4.48,8.68,16.88");
    note("电脑端标曲仅本次会话有效");
    action(
        "生成并保存曲线",
        () -> {
          Calibration c = Calibration.fit(drug, numbers(x.getText()), numbers(peaks.getText()));
          curves.put(drug, c);
          calibration(drug);
        });
    add(
        footer,
        control(
            "恢复默认标曲",
            false,
            () -> {
              curves.put(drug, Calibration.demo(drug));
              calibration(drug);
            }),
        36,
        0);
    render();
  }

  private double[] numbers(String value) {
    String[] fields = value.split(",", -1);
    double[] n = new double[fields.length];
    for (int i = 0; i < n.length; i++) n[i] = Double.parseDouble(fields[i].trim());
    return n;
  }

  private void settings() {
    page("系统设置", "设备与检测参数", true);
    JPanel p = card();
    text(p, "检测设置", 12, MUTED);
    add(p, control("标准曲线管理", false, this::authorization), 43, 8);
    add(
        p,
        control(
            "反应时间    " + minutes + " 分钟",
            false,
            () -> {
              minutes = minutes == 5 ? 10 : 5;
              settings();
            }),
        43,
        8);
    JCheckBox speed = new JCheckBox("演示加速计时（5 秒）", accelerated);
    speed.setFont(font(12, false));
    speed.setOpaque(false);
    speed.addActionListener(e -> accelerated = speed.isSelected());
    add(p, speed, 35, 0);
    JPanel system = card();
    text(system, "系统信息", 12, MUTED);
    text(system, "当前时间    " + date.format(new Date()), 11, INK);
    text(system, "终端电量    电脑端不提供", 11, INK);
    text(system, "版本    0.2.0 公开演示版", 11, INK);
    note("时间跟随电脑系统");
    render();
  }

  @Override
  public void dispose() {
    cancel();
    device.disconnect();
    super.dispose();
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
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
      double min = Arrays.stream(y).min().orElse(0),
          max = Arrays.stream(y).max().orElse(1),
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
        if (i == 0) line.moveTo(xx, yy);
        else line.lineTo(xx, yy);
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
