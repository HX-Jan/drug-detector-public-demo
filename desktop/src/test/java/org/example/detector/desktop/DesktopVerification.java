package org.example.detector.desktop;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.example.detector.core.*;

/** Drives the application's own controls and captures actual portrait UI renders. */
public final class DesktopVerification {
  private static DesktopApp app;

  private static JButton find(Container parent, String label) {
    if (!parent.isVisible()) return null;
    for (Component c : parent.getComponents()) {
      if (c instanceof JButton && label.equals(((JButton) c).getText()) && c.isVisible())
        return (JButton) c;
      if (c instanceof Container) {
        JButton found = find((Container) c, label);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static void click(String label) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JButton b = find(app, label);
          if (b == null || !b.isEnabled()) throw new AssertionError("Unavailable: " + label);
          b.doClick();
        });
  }

  private static void waitFor(String label) throws Exception {
    long limit = System.nanoTime() + 10_000_000_000L;
    while (System.nanoTime() < limit) {
      AtomicReference<Boolean> ready = new AtomicReference<>(false);
      SwingUtilities.invokeAndWait(
          () -> {
            JButton b = find(app, label);
            ready.set(b != null && b.isEnabled() && !app.getGlassPane().isVisible());
          });
      if (ready.get()) return;
      Thread.sleep(80);
    }
    throw new AssertionError("Timed out: " + label);
  }

  private static void capture(Path file) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            JRootPane root = app.getRootPane();
            BufferedImage image =
                new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
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
    Files.createDirectories(evidence);
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              app = new DesktopApp(data);
              app.setVisible(true);
            } catch (Exception ex) {
              throw new RuntimeException(ex);
            }
          });
      capture(evidence.resolve("home.png"));
      click("连接设备");
      click("连接模拟设备");
      waitFor("开始样品测试");
      capture(evidence.resolve("home-connected.png"));
      click("开始样品测试");
      click("开始测试");
      capture(evidence.resolve("workflow.png"));
      click("开始活化");
      Thread.sleep(200);
      capture(evidence.resolve("activation.png"));
      waitFor("已加样，开始反应计时");
      click("已加样，开始反应计时");
      AtomicReference<Boolean> enabled = new AtomicReference<>();
      SwingUtilities.invokeAndWait(() -> enabled.set(find(app, "计时结束后继续").isEnabled()));
      if (enabled.get()) throw new AssertionError("Early advancement allowed");
      capture(evidence.resolve("reaction.png"));
      waitFor("计时结束后继续");
      click("计时结束后继续");
      click("开始检测");
      Thread.sleep(200);
      capture(evidence.resolve("measurement.png"));
      waitFor("导出检测数据");
      if (new FileResultRepository(data).all().size() != 1)
        throw new AssertionError("Result not persisted");
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
      click("标准曲线管理");
      capture(evidence.resolve("authorization.png"));
      // Wrong and correct demo codes exercise the visible keypad, not a test bypass.
      click("0");
      click("确认");
      if (!app.getGlassPane().isVisible()) throw new AssertionError("Incorrect PIN accepted");
      for (String key : new String[] {"1", "2", "3", "4", "5", "6"}) click(key);
      click("确认");
      click("查看 / 绘制标曲 · 甲基苯丙胺");
      click("生成并保存曲线");
      capture(evidence.resolve("calibration.png"));
      // Also inspect the compact supported window size for clipping.
      SwingUtilities.invokeAndWait(() -> app.setSize(380, 660));
      capture(evidence.resolve("compact-calibration.png"));
      SwingUtilities.invokeAndWait(() -> app.dispose());
      SwingUtilities.invokeAndWait(
          () -> {
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
      System.out.println(
          "PASS: portrait UI workflow, countdown gate, modal progress, keypad rejection/acceptance,"
              + " charts, history, persistence, restart and compact viewport; 14 renders saved");
    } finally {
      if (app != null) SwingUtilities.invokeAndWait(() -> app.dispose());
      try (DirectoryStream<Path> files = Files.newDirectoryStream(data)) {
        for (Path p : files) Files.delete(p);
      }
      Files.delete(data);
    }
  }
}
