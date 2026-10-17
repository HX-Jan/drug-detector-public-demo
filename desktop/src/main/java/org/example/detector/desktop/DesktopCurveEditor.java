package org.example.detector.desktop;

import static org.example.detector.desktop.ReferenceTheme.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.function.*;
import javax.swing.*;
import javax.swing.border.*;
import org.example.detector.core.*;

/** 将共享五步流程映射为 Swing 原生组件；布局尺寸依据文档的竖屏截图。 */
final class DesktopCurveEditor implements CurveWorkflow.View {
  private final DesktopApp app;
  private final CurveWorkflow workflow;
  private JLabel currentToast;
  DesktopCurveEditor(DesktopApp app, StandardCurve curve) {
    this.app = app;
    workflow = new CurveWorkflow(this, app.library, curve, () -> {
    });
  }
  void start() {
    workflow.show(0);
    app.render();
  }

  @Override
  public void page(String title, String subtitle, String[] tabs, int selected, IntConsumer change) {
    app.page(title, subtitle, true);
    JPanel row = new JPanel(new GridLayout(1, tabs.length));
    row.setOpaque(false);
    for (int i = 0; i < tabs.length; i++) {
      int n = i;
      JButton b = new JButton(tabs[i]);
      b.setContentAreaFilled(false);
      b.setFocusPainted(false);
      b.addActionListener(e -> app.safe(() -> change.accept(n)));
      b.setFont(font(tabs.length == 5 ? 11 : 12, false));
      b.setForeground(i == selected ? BLUE : MUTED);
      b.setBorder(
          new CompoundBorder(new MatteBorder(0, 0, 2, 0, i == selected ? BLUE : Color.WHITE),
              new EmptyBorder(8, 0, 10, 0)));
      row.add(b);
    }
    app.add(app.body, row, 45, 18);
    // 复用标题返回控件，避免从编辑误跳到首页。
    replaceBack(app.screen, app::calibrationManager);
    app.render();
  }
  private void replaceBack(Container p, Runnable action) {
    for (Component c : p.getComponents()) {
      if (c instanceof JButton && "home".equals(c.getName())) {
        JButton b = (JButton) c;
        for (java.awt.event.ActionListener l : b.getActionListeners())
          b.removeActionListener(l);
        b.addActionListener(e -> app.safe(action));
        return;
      }
      if (c instanceof Container)
        replaceBack((Container) c, action);
    }
  }
  @Override
  public void text(String value, boolean muted) {
    JPanel p = app.card();
    app.text(p, value, 12, muted ? MUTED : INK);
    app.render();
  }
  @Override
  public void baseline(String value, Runnable rescan) {
    JPanel p = app.card();
    JPanel row = new JPanel(new BorderLayout(6, 0));
    row.setOpaque(false);
    row.add(label(value, 10, INK));
    JButton button = app.control("重新采集", false, rescan);
    button.setFont(font(10, false));
    row.add(button, BorderLayout.EAST);
    app.add(p, row, 36, 0);
    app.render();
  }
  @Override
  public void field(String label, String value, boolean alphabet, Consumer<String> changed) {
    app.text(app.body, label, 11, MUTED);
    JButton b = app.control(value.isEmpty() ? "点击输入" : value, false,
        () -> keyboard(label, value, alphabet, changed));
    b.setName(label);
    b.setHorizontalAlignment(SwingConstants.LEFT);
    b.setForeground(INK);
    app.add(app.body, b, 42, 15);
    app.render();
  }
  @Override
  public void choice(String label, String[] options, int selected, IntConsumer changed) {
    app.text(app.body, label, 11, MUTED);
    JComboBox<String> box = new JComboBox<>(options);
    box.setSelectedIndex(selected);
    box.setFont(font(12, false));
    box.setBackground(Color.WHITE);
    box.addActionListener(e -> app.safe(() -> changed.accept(box.getSelectedIndex())));
    app.add(app.body, box, 42, 16);
    app.render();
  }
  @Override
  public void range(String title, double start, double end, DoubleConsumer startChanged,
      DoubleConsumer endChanged) {
    app.text(app.body, title + " (mV)", 11, MUTED);
    JPanel row = new JPanel(new GridBagLayout());
    row.setOpaque(false);
    String[] labels = {title + "起点 (mV)", title + "终点 (mV)"};
    double[] values = {start, end};
    DoubleConsumer[] actions = {startChanged, endChanged};
    for (int i = 0; i < 2; i++) {
      int n = i;
      JButton b = app.control(StandardCurve.number(values[i]), false,
          () -> keyboard(labels[n], StandardCurve.number(values[n]), false, s -> {
            double v = StandardCurve.parseNumber(s);
            StandardCurve.finite(v);
            actions[n].accept(v);
          }));
      b.setName(labels[i]);
      b.setHorizontalAlignment(SwingConstants.LEFT);
      b.setForeground(INK);
      GridBagConstraints gc = new GridBagConstraints();
      gc.gridx = i * 2;
      gc.weightx = 1;
      gc.weighty = 1;
      gc.fill = GridBagConstraints.BOTH;
      row.add(b, gc);
      if (i == 0) {
        gc = new GridBagConstraints();
        gc.gridx = 1;
        gc.insets = new Insets(0, 10, 0, 10);
        row.add(label("至", 12, MUTED), gc);
      }
    }
    app.add(app.body, row, 42, 18);
    app.render();
  }
  @Override
  public void buttons(String[] labels, boolean[] primary, Runnable[] actions, boolean footer) {
    JPanel row = new JPanel(new GridLayout(1, labels.length, 8, 0));
    row.setOpaque(false);
    for (int i = 0; i < labels.length; i++)
      row.add(app.control(labels[i], primary[i], actions[i]));
    app.add(footer ? app.footer : app.body, row, 46, footer ? 0 : 12);
    app.render();
  }
  @Override
  public void plot(Signal signal, String unit, String title) {
    JPanel p = app.card();
    app.add(p, new DesktopApp.Plot(signal, unit, title), 255, 0);
    app.render();
  }
  @Override
  public void progress(String title, String message, int count, IntConsumer acquire, Runnable complete) {
    round(title, message, 1, count, acquire, complete);
  }
  private void round(String title, String message, int n, int count, IntConsumer acquire, Runnable complete) {
    app.progress(title, "第 " + n + "/" + count + " 次 · " + message, 450, () -> {
      acquire.accept(n - 1);
      if (n < count)
        round(title, message, n + 1, count, acquire, complete);
      else
        complete.run();
    });
  }
  @Override
  public void confirm(String title, String message, Runnable confirmed) {
    JPanel p = panel();
    p.setPreferredSize(new Dimension(315, 245));
    JLabel heading = label(title, 17, INK);
    heading.setFont(font(17, true));
    heading.setHorizontalAlignment(SwingConstants.CENTER);
    app.add(p, heading, 35, 8);
    app.add(p, label(message, 12, INK), 95, 15);
    JPanel row = new JPanel(new GridLayout(1, 2, 8, 0));
    row.setOpaque(false);
    row.add(app.control("取消", false, app::hideOverlay));
    JButton confirm = app.control(title.startsWith("删除") ? "确认删除" : "确认", true, () -> {
      app.hideOverlay();
      confirmed.run();
    });
    if (title.startsWith("删除"))
      confirm.putClientProperty("danger", true);
    row.add(confirm);
    app.add(p, row, 42, 0);
    app.showOverlay(p);
  }
  @Override
  public void message(String message) {
    if (currentToast != null && currentToast.getParent() != null)
      currentToast.getParent().remove(currentToast);
    JLabel toast = label(message, 12, Color.WHITE);
    toast.setOpaque(true);
    toast.setBackground(new Color(36, 66, 97));
    toast.setBorder(new EmptyBorder(8, 10, 8, 10));
    toast.setHorizontalAlignment(SwingConstants.CENTER);
    app.add(app.footer, toast, 0, 0);
    app.render();
    currentToast = toast;
    javax.swing.Timer hide = new javax.swing.Timer(4500, e -> {
      if (toast.getParent() != null) {
        toast.getParent().remove(toast);
        app.render();
      }
    });
    hide.setRepeats(false);
    hide.start();
  }
  @Override
  public void details(String title, String text) {
    JPanel p = panel();
    p.setPreferredSize(new Dimension(330, 520));
    app.add(p, label(title, 16, INK), 35, 12);
    JTextArea area = new JTextArea(text);
    area.setEditable(false);
    area.setFont(font(12, false));
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    JScrollPane scroll = new JScrollPane(area);
    scroll.setBorder(null);
    app.add(p, scroll, 380, 12);
    app.add(p, app.control("关闭", true, app::hideOverlay), 42, 0);
    app.showOverlay(p);
  }
  private Path usb() {
    for (File root : File.listRoots()) {
      try {
        String type = javax.swing.filechooser.FileSystemView.getFileSystemView()
            .getSystemTypeDescription(root);
        if (type != null && (type.contains("可移动")
            || type.toLowerCase(java.util.Locale.ROOT).contains("removable")))
          return root.toPath();
      } catch (SecurityException ignored) {
        /* 无权限的盘符不作为导出目标。 */}
    }
    return null;
  }
  @Override
  public String destination() {
    Path usb = usb();
    return "本地：" + app.dataDirectory.resolve("exports/standard_curves").toAbsolutePath() + "\nU 盘："
        + (usb == null ? "未检测到（插入后重试）" : usb);
  }
  @Override
  public void export(StandardCurve curve, boolean removable) {
    Path target = removable ? usb() : app.dataDirectory.resolve("exports/standard_curves");
    if (target == null) {
      message("未检测到 U 盘，请插入后重试");
      return;
    }
    if (removable)
      target = target.resolve("standard_curves");
    try {
      Path output = CurveExport.write(curve, target);
      message("已导出 JSON 与 Excel\n" + output.getFileName());
    } catch (IOException e) {
      throw new IllegalStateException("导出失败：" + e.getMessage(), e);
    }
  }
  /** 参数点击后弹出触屏键盘；只有确认时提交，取消不改变草稿。 */
  private void keyboard(String title, String initial, boolean alphabet,
      Consumer<String> confirmed) {
    JPanel p = panel();
    p.setPreferredSize(new Dimension(330, alphabet ? 455 : 400));
    JLabel heading = label("输入" + title, 16, INK);
    heading.setHorizontalAlignment(SwingConstants.CENTER);
    app.add(p, heading, 32, 8);
    String cleaned = initial.replace("pg/mL", "").trim();
    if (cleaned.equals("未计算"))
      cleaned = "";
    JTextField input = ReferenceTheme.field(cleaned);
    input.setName("keypad-input");
    input.setHorizontalAlignment(SwingConstants.CENTER);
    app.add(p, input, 42, 12);
    JPanel keys = new JPanel(new GridLayout(0, alphabet ? 5 : 4, 6, 6));
    keys.setOpaque(false);
    Runnable submit = () -> {
      confirmed.accept(input.getText().trim());
      app.hideOverlay();
    };
    if (alphabet) {
      alphabet(keys, input, 0);
      app.add(p, keys, 200, 12);
      JPanel row = new JPanel(new GridLayout(1, 4, 6, 0));
      row.setOpaque(false);
      for (String key : new String[]{"删除", "清除", "取消", "确认"})
        row.add(app.control(key, key.equals("确认"), () -> editKey(key, input, submit)));
      app.add(p, row, 42, 0);
    } else {
      for (String key : new String[]{"1", "2", "3", "删除", "4", "5", "6", "清除", "7", "8", "9", "取消",
          ".", "0", "-", "确认"})
        keys.add(app.control(key, key.equals("确认"), () -> editKey(key, input, submit)));
      app.add(p, keys, 230, 0);
    }
    app.showOverlay(p);
    input.requestFocusInWindow();
    input.selectAll();
  }
  private void editKey(String key, JTextField input, Runnable submit) {
    if (key.equals("确认")) {
      submit.run();
      return;
    }
    if (key.equals("取消")) {
      app.hideOverlay();
      return;
    }
    if (key.equals("清除")) {
      input.setText("");
      return;
    }
    String s = input.getText();
    if (key.equals("删除")) {
      if (!s.isEmpty())
        input.setText(s.substring(0, s.length() - 1));
    } else
      input.setText(s + key);
  }
  /** 英文键盘分页按钮的位置与图14一致，避免在窄屏同时挤入全部字母。 */
  private void alphabet(JPanel keys, JTextField input, int page) {
    keys.removeAll();
    String[] entries = page == 0
        ? new String[]{"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N-Z",
            "0-9"}
        : page == 1
            ? new String[]{"N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "A-M",
                "0-9"}
            : new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "_", "-", "A-M", "N-Z",
                "."};
    for (String key : entries)
      keys.add(app.control(key, false, () -> {
        if (key.equals("N-Z"))
          alphabet(keys, input, 1);
        else if (key.equals("A-M"))
          alphabet(keys, input, 0);
        else if (key.equals("0-9"))
          alphabet(keys, input, 2);
        else
          input.setText(input.getText() + key);
      }));
    keys.revalidate();
    keys.repaint();
  }
}
