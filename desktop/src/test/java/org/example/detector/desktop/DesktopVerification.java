package org.example.detector.desktop;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.example.detector.core.*;

/** Drives this application's own Swing components, without desktop mouse automation. */
public final class DesktopVerification {
  private static DesktopApp app;

  private static JButton find(Container parent, String label) {
    for (Component c : parent.getComponents()) {
      if (c instanceof JButton && label.equals(((JButton) c).getText())) return (JButton) c;
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

  private static void capture(Path file) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            BufferedImage image =
                new BufferedImage(app.getWidth(), app.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            app.printAll(g);
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
      click("连接模拟工作站");
      click("样品测试");
      click("进入检测引导");
      click("已确认电极");
      click("已加样，开始反应计时");
      AtomicReference<Boolean> enabled = new AtomicReference<>();
      SwingUtilities.invokeAndWait(() -> enabled.set(find(app, "计时结束后继续").isEnabled()));
      if (enabled.get()) throw new AssertionError("Early advancement allowed");
      Thread.sleep(5400);
      click("计时结束后继续");
      click("开始模拟检测");
      Thread.sleep(1400);
      if (new FileResultRepository(data).all().size() != 1)
        throw new AssertionError("Result not persisted");
      capture(evidence.resolve("result.png"));
      click("历史记录");
      capture(evidence.resolve("history.png"));
      click("返回首页");
      click("标准曲线");
      click("计算并在本次会话应用标曲");
      capture(evidence.resolve("calibration.png"));
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
      click("历史记录与 Excel 导出");
      if (new FileResultRepository(data).all().size() != 1)
        throw new AssertionError("Restart lost history");
      System.out.println(
          "PASS: desktop UI workflow, countdown gate, persistence, history, calibration, restart;"
              + " four application renders saved");
    } finally {
      if (app != null) SwingUtilities.invokeAndWait(() -> app.dispose());
      try (DirectoryStream<Path> files = Files.newDirectoryStream(data)) {
        for (Path p : files) Files.delete(p);
      }
      Files.delete(data);
    }
  }
}
