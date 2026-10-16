package org.example.detector.desktop;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import org.example.detector.core.*;

/** Desktop companion uses the same workflow, math, signal and export code as Android. */
public final class DesktopApp extends JFrame {
  private final Workstation device = new SimulatedWorkstation();
  private final ResultRepository repository;
  private final EnumMap<DrugType, Calibration> curves = new EnumMap<>(DrugType.class);
  private final JPanel content = new JPanel();
  private DetectionSession session;
  private javax.swing.Timer timer;
  private DetectionResult unsaved;
  private boolean accelerated = true;
  private int minutes = 5;
  private final SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  public DesktopApp(Path data) throws IOException {
    super("毒品直读检测仪 · 公开演示版 v0.1.0");
    repository = new FileResultRepository(data);
    for (DrugType drug : DrugType.values()) curves.put(drug, Calibration.demo(drug));
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    setSize(900, 760);
    setMinimumSize(new Dimension(760, 660));
    setLocationRelativeTo(null);
    setContentPane(new JScrollPane(content));
    home();
  }

  private void page(String title) {
    content.removeAll();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setBorder(BorderFactory.createEmptyBorder(25, 32, 25, 32));
    content.setBackground(new Color(243, 246, 251));
    text("毒品直读检测仪  /  公开演示版", 26);
    text("模拟工作站 · 合成数据 · 不作为真实检测结果", 14);
    text(title, 22);
    text(date.format(new Date()) + "  |  " + (device.connected() ? "模拟设备已连接" : "模拟设备未连接"), 13);
  }

  private void render() {
    content.revalidate();
    content.repaint();
  }

  private JLabel text(String value, int size) {
    JLabel label = new JLabel("<html>" + html(value).replace("\n", "<br>") + "</html>");
    label.setFont(new Font("Microsoft YaHei", Font.PLAIN, size));
    label.setForeground(new Color(32, 51, 77));
    label.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    content.add(label);
    return label;
  }

  private static String html(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private JButton button(String title, Runnable action) {
    JButton button = new JButton(title);
    button.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
    button.setAlignmentX(Component.LEFT_ALIGNMENT);
    button.setMaximumSize(new Dimension(780, 45));
    button.addActionListener(e -> safe(action));
    content.add(button);
    content.add(Box.createVerticalStrut(8));
    return button;
  }

  private JTextField input(String value) {
    JTextField field = new JTextField(value);
    field.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
    field.setMaximumSize(new Dimension(780, 34));
    field.setAlignmentX(Component.LEFT_ALIGNMENT);
    content.add(field);
    return field;
  }

  private JComboBox<DrugType> picker() {
    JComboBox<DrugType> combo = new JComboBox<>(DrugType.values());
    combo.setMaximumSize(new Dimension(780, 34));
    combo.setAlignmentX(Component.LEFT_ALIGNMENT);
    content.add(combo);
    return combo;
  }

  private void safe(Runnable action) {
    try {
      action.run();
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(this, ex.getMessage(), "操作未完成", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void cancel() {
    if (timer != null) timer.stop();
    if (session != null) session.cancel();
    session = null;
  }

  private void home() {
    cancel();
    page("检测工作台");
    button(
        device.connected() ? "断开模拟工作站" : "连接模拟工作站",
        () -> {
          if (device.connected()) device.disconnect();
          else device.connect();
          home();
        });
    button("样品测试", this::newSample);
    button("历史记录与 Excel 导出", () -> history("", null));
    button("标准曲线", this::calibration);
    button("设备设置", this::settings);
    text("Android 与电脑演示入口共用纯 Java 核心。\n真实 USB / BLE 协议尚未接入。", 16);
    render();
  }

  private void newSample() {
    if (unsaved != null) {
      savePending();
      return;
    }
    page("登记样品");
    text("虚构样品编号（1–40 位字母、数字、下划线或短横线）", 15);
    JTextField id = input("DEMO-" + System.currentTimeMillis() % 1000000);
    JComboBox<DrugType> drug = picker();
    button(
        "进入检测引导",
        () -> {
          if (!device.connected()) throw new IllegalStateException("请先连接模拟工作站");
          session =
              new DetectionSession(
                  id.getText().trim(),
                  (DrugType) drug.getSelectedItem(),
                  accelerated ? 5000 : minutes * 60000L,
                  () -> System.nanoTime() / 1000000);
          step();
        });
    button("返回首页", this::home);
    render();
  }

  private void step() {
    page("样品检测 · " + session.drug.label + " · " + session.sampleId);
    switch (session.stage()) {
      case PREPARED:
        text("01  确认电极", 24);
        text("确认试条已插入并保持稳定（人工确认演示）。", 16);
        button(
            "已确认电极",
            () -> {
              session.confirmElectrode();
              step();
            });
        break;
      case SAMPLE_ADDED:
        text("02  滴加样品", 24);
        text("确认原型中的加样步骤（10 μL）。", 16);
        button(
            "已加样，开始反应计时",
            () -> {
              session.confirmSample();
              step();
            });
        break;
      case REACTING:
        text("03  等待反应", 24);
        text(accelerated ? "演示加速：5 秒代表实际 5/10 分钟。" : "常规计时：" + minutes + " 分钟。", 16);
        JLabel left = text("等待计时…", 32);
        JButton next =
            button(
                "计时结束后继续",
                () -> {
                  timer.stop();
                  session.finishReaction();
                  step();
                });
        next.setEnabled(false);
        timer =
            new javax.swing.Timer(
                100,
                e -> {
                  long ms = session.remainingMillis();
                  left.setText(
                      String.format(
                          Locale.ROOT, "%02d:%02d", ms / 60000, (ms % 60000 + 999) / 1000));
                  next.setEnabled(ms == 0);
                  if (ms == 0) timer.stop();
                });
        timer.start();
        break;
      case READY:
        text("04  冲洗与添加反应液", 24);
        text("确认冲洗、去除多余液体及添加反应液（原型为 50 μL）。", 16);
        button("开始模拟检测", this::measure);
        break;
      default:
        text("流程已结束。", 16);
    }
    button("取消并返回首页", this::home);
    render();
  }

  private void measure() {
    session.beginMeasurement(device.connected());
    page("正在模拟采集");
    JProgressBar progress = new JProgressBar(0, 100);
    progress.setMaximumSize(new Dimension(780, 30));
    content.add(progress);
    timer =
        new javax.swing.Timer(
            100,
            e -> {
              int value = progress.getValue() + 10;
              progress.setValue(value);
              if (value >= 100) {
                timer.stop();
                safe(
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
            });
    timer.start();
    render();
  }

  private void savePending() {
    try {
      repository.save(unsaved);
      DetectionResult r = unsaved;
      unsaved = null;
      detail(r);
    } catch (IOException ex) {
      page("结果尚未保存");
      text(ex.getMessage() + "；当前结果只在内存中。", 16);
      button("重试保存", this::savePending);
      render();
    }
  }

  private void detail(DetectionResult r) {
    page("检测结果详情");
    text(String.format(Locale.ROOT, "%.3f ng/L · %s", r.concentration, r.status()), 25);
    text(
        "样品："
            + r.sampleId
            + "  |  "
            + r.drug.label
            + "  |  "
            + date.format(new Date(r.epochMillis)),
        16);
    Plot plot = new Plot(r.signal);
    plot.setAlignmentX(Component.LEFT_ALIGNMENT);
    content.add(plot);
    text(
        String.format(
            Locale.ROOT,
            "I0 %.4f μA   I1 %.4f μA   ΔI %.4f μA",
            r.baselinePeak,
            r.samplePeak,
            r.samplePeak - r.baselinePeak),
        15);
    text(r.calibration.equation() + "\n当次标曲 " + r.calibration.id, 13);
    button("导出本条 Excel", () -> export(Collections.singletonList(r)));
    button("历史记录", () -> history("", null));
    button("返回首页", this::home);
    render();
  }

  private void history(String query, DrugType filter) {
    page("历史记录");
    JTextField search = input(query);
    JComboBox<Object> type = new JComboBox<>();
    type.addItem("全部类型");
    for (DrugType d : DrugType.values()) type.addItem(d);
    if (filter != null) type.setSelectedItem(filter);
    type.setMaximumSize(new Dimension(780, 32));
    type.setAlignmentX(Component.LEFT_ALIGNMENT);
    content.add(type);
    button(
        "按编号与类型查询",
        () ->
            history(
                search.getText().trim(),
                type.getSelectedItem() instanceof DrugType
                    ? (DrugType) type.getSelectedItem()
                    : null));
    try {
      List<DetectionResult> rows = repository.search(query, filter);
      DefaultTableModel model =
          new DefaultTableModel(new String[] {"样品编号", "类型", "模拟浓度 ng/L", "时间"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
              return false;
            }
          };
      for (DetectionResult r : rows)
        model.addRow(
            new Object[] {
              r.sampleId,
              r.drug.label,
              String.format(Locale.ROOT, "%.3f", r.concentration),
              date.format(new Date(r.epochMillis))
            });
      JTable table = new JTable(model);
      table.setRowHeight(32);
      table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      JScrollPane scroll = new JScrollPane(table);
      scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
      scroll.setPreferredSize(new Dimension(780, 220));
      scroll.setMaximumSize(new Dimension(780, 260));
      content.add(scroll);
      text("共 " + rows.size() + " 条模拟记录；Ctrl / Shift 可多选。", 13);
      button(
          "查看所选记录详情",
          () -> {
            if (table.getSelectedRow() < 0) throw new IllegalArgumentException("请先选中一条记录");
            detail(rows.get(table.getSelectedRow()));
          });
      button(
          "导出所选 Excel",
          () -> {
            List<DetectionResult> chosen = new ArrayList<>();
            for (int i : table.getSelectedRows()) chosen.add(rows.get(i));
            export(chosen);
          });
    } catch (IOException ex) {
      text("读取失败：" + ex.getMessage(), 15);
    }
    button("返回首页", this::home);
    render();
  }

  private void export(List<DetectionResult> rows) {
    if (rows.isEmpty()) throw new IllegalArgumentException("请先选择记录");
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
      JOptionPane.showMessageDialog(this, "已导出 " + rows.size() + " 条模拟记录");
    } catch (IOException ex) {
      throw new IllegalStateException("导出失败：" + ex.getMessage(), ex);
    }
  }

  private void calibration() {
    page("演示标准曲线");
    JComboBox<DrugType> drug = picker();
    JLabel current = text(curves.get(DrugType.values()[0]).equation(), 16);
    drug.addActionListener(
        e -> current.setText(curves.get((DrugType) drug.getSelectedItem()).equation()));
    text("5–10 个递增浓度 ng/L（逗号分隔）", 14);
    JTextField x = input("1,10,25,50,100");
    text("I0 到 In，单位 μA；拟合使用相邻电流差值", 14);
    JTextField peaks = input("1,1.28,2.28,4.48,8.68,16.88");
    button(
        "计算并在本次会话应用标曲",
        () -> {
          Calibration c =
              Calibration.fit(
                  (DrugType) drug.getSelectedItem(),
                  numbers(x.getText()),
                  numbers(peaks.getText()));
          curves.put(c.drug, c);
          current.setText(c.equation());
        });
    text("电脑入口的标曲仅本次会话有效；Android 入口会持久保存。\n所有默认参数为合成参数，相关系数由输入计算。", 14);
    button("返回首页", this::home);
    render();
  }

  private double[] numbers(String value) {
    String[] fields = value.split(",", -1);
    double[] n = new double[fields.length];
    for (int i = 0; i < n.length; i++) n[i] = Double.parseDouble(fields[i].trim());
    return n;
  }

  private void settings() {
    page("设备设置");
    button(
        "演示加速计时：" + (accelerated ? "开（5 秒）" : "关"),
        () -> {
          accelerated = !accelerated;
          settings();
        });
    button(
        "常规反应时间：" + minutes + " 分钟",
        () -> {
          minutes = minutes == 5 ? 10 : 5;
          settings();
        });
    text("时间跟随电脑系统。电脑演示入口不读取检测设备电量。\n真实硬件、扫码和系统校时尚未接入。", 16);
    button("返回首页", this::home);
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
            DesktopApp app = new DesktopApp(Paths.get(System.getProperty("detector.data", "data")));
            app.setVisible(true);
          } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, ex.getMessage());
          }
        });
  }

  /** Renders synthetic charts without external libraries. */
  static final class Plot extends JPanel {
    final Signal signal;

    Plot(Signal s) {
      signal = s;
      setPreferredSize(new Dimension(780, 220));
      setMaximumSize(new Dimension(780, 220));
      setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      Graphics2D g = (Graphics2D) graphics;
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      double[] x = signal.voltages(), y = signal.currents();
      double min = Arrays.stream(y).min().orElse(0), max = Arrays.stream(y).max().orElse(1);
      g.setColor(Color.GRAY);
      g.drawLine(55, getHeight() - 35, getWidth() - 20, getHeight() - 35);
      g.drawString("μA", 10, 20);
      g.drawString("V", getWidth() - 20, getHeight() - 12);
      g.drawString(String.format(Locale.ROOT, "%.2f", max), 5, 40);
      g.drawString(String.format(Locale.ROOT, "%.2f", min), 5, getHeight() - 35);
      g.drawString("合成 DPV 信号", 65, 20);
      g.setColor(new Color(30, 104, 211));
      g.setStroke(new BasicStroke(2));
      int px = 0, py = 0;
      for (int i = 0; i < x.length; i++) {
        int xx = 55 + (int) ((x[i] - x[0]) / (x[x.length - 1] - x[0]) * (getWidth() - 75)),
            yy =
                getHeight()
                    - 35
                    - (int) ((y[i] - min) / Math.max(0.1, max - min) * (getHeight() - 70));
        if (i > 0) g.drawLine(px, py, xx, yy);
        px = xx;
        py = yy;
      }
      g.drawString("-0.5", 55, getHeight() - 12);
      g.drawString("0.5", getWidth() - 45, getHeight() - 12);
    }
  }
}
