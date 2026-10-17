package org.example.detector.core;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;

/** 标曲完整流程验收：用已知数值独立验证公式、状态失效、落盘和导出。 */
public final class StandardCurveVerification {
  private static void check(boolean ok, String message) {
    if (!ok)
      throw new AssertionError(message);
  }
  private static void near(double a, double b) {
    check(Math.abs(a - b) < 1e-8, a + " != " + b);
  }
  private static void rejects(Runnable r) {
    try {
      r.run();
    } catch (IllegalArgumentException | IllegalStateException e) {
      return;
    }
    throw new AssertionError("应拒绝非法操作");
  }
  public static void main(String[] args) throws Exception {
    verifySimulatedCurveWorkflow();
    StandardCurve c = new StandardCurve();
    rejects(c::validateInfo);
    c.name = "GH";
    c.scanStart = -500;
    c.scanEnd = 500;
    c.peakStart = -200;
    c.peakEnd = 200;
    c.validateInfo();
    c.activation = signal(0.1);
    c.confirmBaseline(new double[]{1, 1, 1});
    c.addPoint(1, new double[]{1.28, 1.28, 1.28});
    c.addPoint(10, new double[]{2, 2, 2});
    near(c.fit().slope, .08);
    near(c.fit().intercept, .2);
    c.calculateLod(new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
    near(c.mean, 5.5);
    near(c.sd, Math.sqrt(82.5 / 9));
    near(c.lod, 2.82 * c.sd);
    near(c.loq, 4 * c.lod);
    rejects(() -> c.addPoint(1, new double[]{1, 1, 1}));
    rejects(() -> c.manualLod(Double.NaN));
    rejects(() -> c.calculateLod(new double[]{1, 2}));
    Path dir = Files.createTempDirectory("curve-test-");
    try {
      CurveLibrary lib = new CurveLibrary(dir);
      lib.save(c);
      CurveLibrary restart = new CurveLibrary(dir);
      StandardCurve loaded = restart.all().stream().filter(v -> v.id.equals(c.id)).findFirst()
          .orElseThrow();
      near(loaded.lod, c.lod);
      near(loaded.fit().slope, .08);
      check(loaded.activation != null && loaded.activation.voltages().length == 201, "活化曲线完整保留");
      check(loaded.points.size() == 2 && loaded.lodSamples.length == 10, "完整数据重启保留");
      String json = CurveExport.json(loaded);
      check(json.contains("lodSamples") && json.contains("scanStart"),
          "JSON 包含溯源与参数");
      if (args.length > 0)
        CurveExport.write(loaded, Paths.get(args[0]));
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      CurveExport.xlsx(loaded, bytes);
      try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        int sheets = 0;
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null)
          if (entry.getName().startsWith("xl/worksheets/"))
            sheets++;
        check(sheets >= 2, "Excel 实际包含参数及采集数据");
      }
      loaded.removePoint(0);
      check(Double.isNaN(loaded.lod), "改动浓度点后旧 LOD 失效");
      rejects(loaded::fit);
      restart.delete(c.id);
      check(new CurveLibrary(dir).all().stream().noneMatch(v -> v.id.equals(c.id)), "删除重启不复原");
    } finally {
      try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
        for (Path p : files.sorted(Comparator.reverseOrder()).toArray(Path[]::new))
          Files.delete(p);
      }
    }
    System.out.println("PASS: 标曲拟合、LOD/LOQ、校验、失效、重启、删除、JSON/XLSX");
  }
  /** 通过向导采集已知浓度，防止样品编号的哈希值产生负斜率。 */
  private static void verifySimulatedCurveWorkflow() throws Exception {
    Map<String, Runnable> buttons = new HashMap<>();
    Map<String, java.util.function.Consumer<String>> fields = new HashMap<>();
    CurveWorkflow.View view = (CurveWorkflow.View) java.lang.reflect.Proxy.newProxyInstance(
        CurveWorkflow.View.class.getClassLoader(), new Class<?>[]{CurveWorkflow.View.class},
        (proxy, method, values) -> {
          if (method.getName().equals("buttons")) {
            String[] labels = (String[]) values[0];
            Runnable[] actions = (Runnable[]) values[2];
            for (int i = 0; i < labels.length; i++)
              buttons.put(labels[i], actions[i]);
          } else if (method.getName().equals("field")) {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<String> changed =
                (java.util.function.Consumer<String>) values[3];
            fields.put((String) values[0], changed);
          } else if (method.getName().equals("progress")) {
            for (int i = 0; i < (Integer) values[2]; i++)
              ((java.util.function.IntConsumer) values[3]).accept(i);
            ((Runnable) values[4]).run();
          }
          return null;
        });
    StandardCurve curve = new StandardCurve();
    curve.name = "GH";
    curve.scanStart = -500;
    curve.scanEnd = 500;
    curve.peakStart = -200;
    curve.peakEnd = 200;
    curve.activation = signal(.1);
    curve.confirmBaseline(new double[]{1, 1, 1});
    CurveWorkflow workflow = new CurveWorkflow(view, null, curve, () -> {});
    workflow.show(2);
    for (String concentration : new String[]{"1", "10", "20"}) {
      fields.get("浓度").accept(concentration);
      buttons.get("添加浓度").run();
      if (curve.points.size() >= 2) {
        near(curve.fit().slope, .08);
        near(curve.fit().intercept, .2);
      }
    }
    workflow.show(3);
  }
  private static Signal signal(double peak) {
    double[] x = new double[201], y = new double[201];
    for (int i = 0; i < x.length; i++) { x[i] = i; y[i] = peak * i / 200.0; }
    return new Signal(x, y);
  }
}
