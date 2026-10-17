package org.example.detector.desktop;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.example.detector.core.*;

/**
 * Drives the application's own controls and captures actual portrait UI
 * renders.
 */
public final class DesktopVerification {
  private static DesktopApp app;
  private static Path evidenceDirectory;

  private static JButton find(Container parent, String label) {
    if (!parent.isVisible())
      return null;
    for (Component c : parent.getComponents()) {
      if (c instanceof JButton && label.equals(((JButton) c).getText()) && c.isVisible())
        return (JButton) c;
      if (c instanceof Container) {
        JButton found = find((Container) c, label);
        if (found != null)
          return found;
      }
    }
    return null;
  }

  private static void click(String label) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      JButton b = find(app, label);
      if (b == null || !b.isEnabled())
        throw new AssertionError("Unavailable: " + label);
      b.doClick();
    });
  }

  private static void waitFor(String label) throws Exception {
    long limit = System.nanoTime() + 10_000_000_000L;
    while (System.nanoTime() < limit) {
      AtomicReference<Boolean> ready = new AtomicReference<>(false);
      SwingUtilities.invokeAndWait(() -> {
        JButton b = find(app, label);
        ready.set(
            b != null && b.isEnabled() && (!app.getGlassPane().isVisible() || label.equals("确认")));
      });
      if (ready.get())
        return;
      Thread.sleep(80);
    }
    throw new AssertionError("Timed out: " + label);
  }

  private static Component named(Container parent, String name) {
    for (Component c : parent.getComponents()) {
      if (name.equals(c.getName()))
        return c;
      if (c instanceof Container) {
        Component found = named((Container) c, name);
        if (found != null)
          return found;
      }
    }
    return null;
  }
  /** 通过真实字段及屏幕键盘确认来填写，保留完整的用户操作路径。 */
  private static void parameter(String label, String value) throws Exception {
    SwingUtilities.invokeAndWait(() -> ((JButton) named(app, label)).doClick());
    SwingUtilities.invokeAndWait(() -> ((JTextField) named(
        app.getGlassPane() instanceof Container ? (Container) app.getGlassPane() : app,
        "keypad-input")).setText(value));
    if (label.equals("英文名") || label.equals("浓度"))
      capture(evidenceDirectory
          .resolve(label.equals("英文名") ? "english-keypad.png" : "numeric-keypad.png"));
    click("确认");
  }

  /** 找到界面上第一个可编辑文本框，用于通过真实输入路径填写样品编号。 */
  private static JTextField firstEditableField(Container root) {
    java.util.Deque<Component> queue = new java.util.ArrayDeque<>();
    queue.add(root);
    while (!queue.isEmpty()) {
      Component c = queue.poll();
      if (c instanceof JTextField && c.isVisible() && ((JTextField) c).isEditable())
        return (JTextField) c;
      if (c instanceof Container) {
        for (Component child : ((Container) c).getComponents())
          queue.add(child);
      }
    }
    return null;
  }

  private static void capture(Path file) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        JRootPane root = app.getRootPane();
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(),
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        root.printAll(g);
        g.dispose();
        ImageIO.write(image, "png", file.toFile());
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  public static void main(String[] args) throws Exception {
    Path data = Files.createTempDirectory("detector-ui-");
    Path evidence = Paths.get(args.length == 0 ? "build/ui-evidence" : args[0]);
    evidenceDirectory = evidence;
    Files.createDirectories(evidence);
    try {
      SwingUtilities.invokeAndWait(() -> {
        try {
          app = new DesktopApp(data);
          app.setVisible(true);
        } catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      });
      capture(evidence.resolve("home.png"));
      if (app.library.all().stream().map(c -> c.drug).distinct().count() != 4)
        throw new AssertionError("Fresh library must offer four drug types");
      waitFor("开始样品测试");
      capture(evidence.resolve("home-connected.png"));
      click("开始样品测试");
      capture(evidence.resolve("workflow.png"));
      // 样品编号通过页面输入框填写，保持真实用户路径。
      SwingUtilities.invokeAndWait(() -> {
        JTextField field = firstEditableField(app);
        if (field != null)
          field.setText("UI-001");
      });
      click("开始活化");
      Thread.sleep(200);
      capture(evidence.resolve("activation.png"));
      waitFor("已加样，开始反应计时");
      click("已加样，开始反应计时");
      AtomicReference<Boolean> enabled = new AtomicReference<>();
      SwingUtilities.invokeAndWait(() -> enabled.set(find(app, "计时结束后继续").isEnabled()));
      if (enabled.get())
        throw new AssertionError("Early advancement allowed");
      waitFor("跳过计时（展示模式）");
      capture(evidence.resolve("reaction.png"));
      click("跳过计时（展示模式）");
      click("开始检测");
      Thread.sleep(200);
      capture(evidence.resolve("measurement.png"));
      waitFor("导出检测数据");
      if (new FileResultRepository(data).all().size() != 1)
        throw new AssertionError("Result not persisted");
      if (new FileResultRepository(data).all().get(0).origin != DataOrigin.SIMULATED)
        throw new AssertionError("Result must retain simulated origin");
      capture(evidence.resolve("result.png"));
      click("DPV 曲线");
      capture(evidence.resolve("signal.png"));
      click("计算详情");
      capture(evidence.resolve("calculation.png"));
      click("历史记录");
      capture(evidence.resolve("history.png"));
      click("‹");
      click("系统设置");
      capture(evidence.resolve("settings.png"));
      click("开启管理员模式");
      capture(evidence.resolve("authorization.png"));
      // Wrong and correct demo codes exercise the visible keypad, not a test bypass.
      click("0");
      click("确认");
      if (!app.getGlassPane().isVisible())
        throw new AssertionError("Incorrect PIN accepted");
      for (String key : new String[]{"1", "2", "3", "4", "5", "6"})
        click(key);
      click("确认");
      click("标准曲线管理");
      int curvesBeforeDelete = app.library.all().size();
      SwingUtilities.invokeAndWait(() -> {
        for (Component c : app.body.getComponents())
          if (c instanceof JRadioButton) {
            ((JRadioButton) c).doClick();
            break;
          }
      });
      click("删除");
      capture(evidence.resolve("curve-delete-confirm.png"));
      click("取消");
      if (app.library.all().size() != curvesBeforeDelete)
        throw new AssertionError("Cancel deleted curve");
      click("删除");
      click("确认删除");
      if (app.library.all().size() != curvesBeforeDelete - 1)
        throw new AssertionError("Curve deletion failed");
      click("添加");
      SwingUtilities.invokeAndWait(() -> {
        for (Component c : app.body.getComponents())
          if (c instanceof JComboBox) {
            JComboBox<?> types = (JComboBox<?>) c;
            if (types.getItemCount() != 4) throw new AssertionError("Missing drug choices");
            types.setSelectedIndex(DrugType.METHADONE.ordinal());
            break;
          }
      });
      capture(evidence.resolve("curve-info.png"));
      parameter("英文名", "GH");
      parameter("扫描范围起点 (mV)", "-500");
      parameter("扫描范围终点 (mV)", "500");
      parameter("寻峰范围起点 (mV)", "-200");
      parameter("寻峰范围终点 (mV)", "200");
      click("下一步：活化参数");
      capture(evidence.resolve("curve-activation.png"));
      click("自动活化");
      Thread.sleep(100);
      capture(evidence.resolve("curve-activation-progress.png"));
      waitFor("下一步：曲线");
      click("下一步：曲线");
      click("采集基线");
      waitFor("确认");
      capture(evidence.resolve("curve-baseline-confirm.png"));
      click("确认");
      click("添加浓度");
      Thread.sleep(1700);
      waitFor("下一步：LOD/LOQ");
      parameter("浓度", "10");
      click("添加浓度");
      Thread.sleep(1700);
      capture(evidence.resolve("curve-points.png"));
      click("下一步：LOD/LOQ");
      SwingUtilities.invokeAndWait(() -> {
        for (Component c : app.body.getComponents())
          if (c instanceof JComboBox)
            ((JComboBox<?>) c).setSelectedIndex(1);
      });
      capture(evidence.resolve("curve-lod.png"));
      click("采集 10 条并计算");
      Thread.sleep(5000);
      capture(evidence.resolve("curve-lod-result.png"));
      click("查看详细计算过程");
      capture(evidence.resolve("curve-lod-details.png"));
      click("关闭");
      click("下一步：导出与保存");
      click("结果保存");
      click("导出本地");
      StandardCurve savedCurve = app.library.all().stream().filter(c -> c.name.equals("GH")).findFirst().orElseThrow();
      if (savedCurve.drug != DrugType.METHADONE || savedCurve.origin != DataOrigin.SIMULATED
          || savedCurve.baselineSignals.length != 3 || savedCurve.points.get(0).repeats.length != 3 || savedCurve.lodSignals.length != 10)
        throw new AssertionError("Saved curve lost type, origin or repeated traces");
      capture(evidence.resolve("curve-export.png"));
      if (new CurveLibrary(data.resolve("curves")).all().stream()
          .noneMatch(c -> c.name.equals("GH")))
        throw new AssertionError("Curve not persisted");
      try (java.util.stream.Stream<Path> files = Files
          .list(data.resolve("exports/standard_curves"))) {
        if (files.count() != 2)
          throw new AssertionError("Export not written");
      }
      click("查看曲线");
      capture(evidence.resolve("curve-viewer.png"));
      click("方程");
      click("LOD/LOQ");
      click("返回编辑");
      capture(evidence.resolve("calibration.png"));
      // Also inspect the compact supported window size for clipping.
      SwingUtilities.invokeAndWait(() -> app.setSize(380, 660));
      Thread.sleep(250);
      capture(evidence.resolve("compact-calibration.png"));
      SwingUtilities.invokeAndWait(() -> app.dispose());
      SwingUtilities.invokeAndWait(() -> {
        try {
          app = new DesktopApp(data);
          app.setVisible(true);
        } catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      });
      click("历史记录");
      if (new FileResultRepository(data).all().size() != 1)
        throw new AssertionError("Restart lost history");
      System.out.println("PASS: 检测闭环、倒计时门禁、扫描连接、管理员口令、标曲删除取消/确认、五步向导、三次采集、十次LOD、实际导出、重启、紧凑窗口与截图");
    } finally {
      if (app != null)
        SwingUtilities.invokeAndWait(() -> app.dispose());
      try (java.util.stream.Stream<Path> files = Files.walk(data)) {
        for (Path p : files.sorted(Comparator.reverseOrder()).toArray(Path[]::new))
          Files.delete(p);
      }
    }
  }
}
