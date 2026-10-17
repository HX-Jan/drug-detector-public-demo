package org.example.detector.core;

import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.*;

/** 独立计数设备验证每次采集、原始数据、单位、来源及初始化状态。 */
public final class AcquisitionVerification {
  private static int checks;

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static void near(double a, double b) {
    check(Math.abs(a - b) < 1e-8, a + " != " + b);
  }

  private static final class CountingDevice implements Workstation {
    int bases, standards, activations;

    public void connect() {}

    public void disconnect() {}

    public boolean connected() {
      return true;
    }

    public DataOrigin origin() {
      return DataOrigin.SIMULATED;
    }

    public Signal baseline() {
      throw new AssertionError("未配置的基线接口被调用");
    }

    public Signal sample(String id, DrugType type) {
      throw new AssertionError("样品接口不能用于标曲");
    }

    public Signal activation(StandardCurve c) {
      activations++;
      return new Signal(new double[] {.025, .4, .8}, new double[] {1, 2, 1});
    }

    public Signal baseline(StandardCurve c, int repeat) {
      bases++;
      return trace(c, 1 + repeat * .01);
    }

    public Signal sampleStandard(
        double concentration, StandardCurve c, int repeat, int repetitions) {
      standards++;
      // 曲线重复有对称偏差；LOD 偏差使用平方序列，能检测控制器是否偷偷硬编码线性噪声。
      double noise = repetitions == 10 ? repeat * repeat * .001 : (repeat - 1) * .01;
      return trace(c, 1.01 + .2 + .08 * concentration + noise);
    }

    private Signal trace(StandardCurve c, double peak) {
      return new Signal(
          new double[] {c.scanStart / 1000, (c.peakStart + c.peakEnd) / 2000, c.scanEnd / 1000},
          new double[] {0, peak, 0});
    }
  }

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("acquisition-regression-");
    CurveLibrary library = new CurveLibrary(root.resolve("fresh"));
    DemoCatalog.ensure(library);
    check(
        library.all().size() == 4
            && library.all().stream().map(c -> c.drug).distinct().count() == 4,
        "新库提供四种类型");
    library.delete("DEMO-METH-CURVE");
    StandardCurve renamed = DemoCatalog.defaultCurve();
    renamed.id = "replacement";
    library.save(renamed);
    library = new CurveLibrary(root.resolve("fresh"));
    DemoCatalog.ensure(library);
    check(
        library.all().size() == 4
            && library.all().stream().noneMatch(c -> c.id.equals("DEMO-METH-CURVE")),
        "删除后同名新标曲不导致启动冲突");
    for (StandardCurve c : library.all()) library.delete(c.id);
    library = new CurveLibrary(root.resolve("fresh"));
    DemoCatalog.ensure(library);
    check(library.all().isEmpty(), "清空库重启不恢复模板");

    StandardCurve c = new StandardCurve();
    c.name = "REPEAT";
    c.scanStart = -500;
    c.scanEnd = 800;
    c.peakStart = 250;
    c.peakEnd = 500;
    CountingDevice device = new CountingDevice();
    Map<String, Runnable> buttons = new HashMap<>();
    Map<String, Consumer<String>> fields = new HashMap<>();
    Map<String, IntConsumer> choices = new HashMap<>();
    CurveWorkflow.View view =
        (CurveWorkflow.View)
            Proxy.newProxyInstance(
                CurveWorkflow.View.class.getClassLoader(),
                new Class<?>[] {CurveWorkflow.View.class},
                (p, m, v) -> {
                  switch (m.getName()) {
                    case "buttons":
                      String[] labels = (String[]) v[0];
                      Runnable[] actions = (Runnable[]) v[2];
                      for (int i = 0; i < labels.length; i++) buttons.put(labels[i], actions[i]);
                      break;
                    case "field":
                      fields.put((String) v[0], (Consumer<String>) v[3]);
                      break;
                    case "choice":
                      choices.put((String) v[0], (IntConsumer) v[3]);
                      break;
                    case "progress":
                      for (int i = 0; i < (Integer) v[2]; i++) ((IntConsumer) v[3]).accept(i);
                      ((Runnable) v[4]).run();
                      break;
                    case "confirm":
                      ((Runnable) v[2]).run();
                      break;
                  }
                  return null;
                });
    CurveWorkflow flow = new CurveWorkflow(view, library, c, () -> {}, device);
    flow.show(0);
    choices.get("检测类型").accept(DrugType.METHADONE.ordinal());
    check(c.drug == DrugType.METHADONE, "向导可选择美沙酮");
    flow.show(1);
    buttons.get("自动活化").run();
    check(device.activations == 1 && c.activationUnitVerified, "活化走专用适配器");
    near(c.activation.voltages()[0], 25);
    near(c.activation.voltages()[2], 800);
    flow.show(2);
    buttons.get("采集基线").run();
    check(device.bases == 3 && c.baselineSignals.length == 3, "三次基线各调用设备并保留轨迹");
    near(c.baseline[0], 1);
    near(c.baseline[2], 1.02);
    buttons.get("添加浓度").run();
    fields.get("浓度").accept("10");
    buttons.get("添加浓度").run();
    check(device.standards == 6 && c.points.get(0).repeats.length == 3, "两个浓度实际采集六次");
    near(c.fit().slope, .08);
    near(c.fit().intercept, .2);
    near(c.points.get(0).signal.voltages()[1], 375);
    flow.show(3);
    choices.get("LOD 确定方式").accept(1);
    buttons.get("采集 10 条并计算").run();
    check(device.standards == 16 && c.lodSignals.length == 10, "LOD 实际另采十条");
    near(c.lodSamples[0], 1);
    near(c.lodSamples[9], 1 + .081 / .08);
    double mean = 0, sum = 0;
    for (int i = 0; i < 10; i++) mean += (1 + i * i * .001 / .08) / 10;
    for (int i = 0; i < 10; i++) sum += Math.pow(1 + i * i * .001 / .08 - mean, 2);
    near(c.sd, Math.sqrt(sum / 9));
    near(c.lod, 2.82 * Math.sqrt(sum / 9));
    check(c.origin == DataOrigin.SIMULATED, "标曲记录合成来源");
    library.save(c);
    StandardCurve loaded = new CurveLibrary(root.resolve("fresh")).all().get(0);
    check(loaded.drug == DrugType.METHADONE && loaded.origin == DataOrigin.SIMULATED, "类型及来源重启保留");
    check(
        loaded.baselineSignals.length == 3
            && loaded.points.get(1).repeats.length == 3
            && loaded.lodSignals.length == 10,
        "逐次原始曲线落盘保留");
    near(loaded.points.get(1).repeats[2].currents()[1], 2.02);
    String json = CurveExport.json(loaded);
    check(
        json.contains("SIMULATED")
            && json.contains("baselineSignals")
            && json.contains("lodSignals")
            && json.contains("\"repeats\""),
        "JSON 包含来源与逐次轨迹");
    ByteArrayOutputStream xlsx = new ByteArrayOutputStream();
    CurveExport.xlsx(loaded, xlsx);
    boolean source = false, repeats = false;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx.toByteArray()))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        String text = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (entry.getName().equals("xl/worksheets/sheet2.xml")) {
          source = text.contains("SIMULATED");
          repeats = text.contains("浓度原始曲线") && text.contains("LOD 原始曲线") && text.contains("mV");
        }
      }
    }
    check(source && repeats, "Excel 包含来源、单位及逐次原始轨迹");
    Properties legacy = new Properties();
    try (InputStream in = Files.newInputStream(root.resolve("fresh/standard-curves.properties"))) {
      legacy.load(in);
    }
    legacy.remove("c0.origin");
    legacy.remove("c0.activationUnitVerified");
    legacy.setProperty("c0.activationX", "0.025,0.4,0.8");
    legacy.setProperty("c0.activationY", "1,2,1");
    Path legacyDirectory = Files.createDirectories(root.resolve("legacy"));
    try (OutputStream out =
        Files.newOutputStream(legacyDirectory.resolve("standard-curves.properties"))) {
      legacy.store(out, "legacy fixture");
    }
    StandardCurve legacyCurve = new CurveLibrary(legacyDirectory).all().get(0);
    check(
        legacyCurve.origin == DataOrigin.UNKNOWN && !legacyCurve.activationUnitVerified,
        "旧标曲缺失元数据时不猜测来源或单位");
    near(legacyCurve.activation.voltages()[0], .025);
    check(
        CurveExport.json(legacyCurve).contains("\"activationVoltageUnit\":\"UNKNOWN\""),
        "旧活化导出明确保留未知单位");

    SimulatedWorkstation simulator = new SimulatedWorkstation();
    simulator.connect();
    Signal configured = simulator.sampleStandard(1, c, 0, 3);
    near(configured.voltages()[0], -.5);
    near(configured.voltages()[configured.voltages().length - 1], .8);
    check(configured.voltages().length == 261, "扫描范围和步进影响实际采样点数");
    c.step = 10;
    check(simulator.sampleStandard(1, c, 0, 3).voltages().length == 131, "更改步进后重新生成采样网格");
    c.startPotential = 100;
    c.endPotential = 900;
    Signal activation = simulator.activation(c);
    near(activation.voltages()[0], .1);
    near(activation.voltages()[activation.voltages().length - 1], .9);

    DetectionResult result =
        new DetectionResult(
            "r", "SAMPLE", c.drug, 1, 1, 2, c.fit(), configured, DataOrigin.SIMULATED);
    check(
        ResultCodec.decode(ResultCodec.encode(result)).origin == DataOrigin.SIMULATED,
        "检测记录来源往返保留");
    // 固定的 v1 字节布局独立于新编码器，验证旧记录可读且不会被假定为设备实测。
    ByteArrayOutputStream old = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(old)) {
      out.writeInt(0x44545031);
      out.writeUTF("old");
      out.writeUTF("LEGACY");
      out.writeUTF(c.drug.name());
      out.writeLong(1);
      out.writeDouble(1);
      out.writeDouble(2);
      Calibration fit = c.fit();
      out.writeUTF(fit.id);
      for (double d : new double[] {fit.slope, fit.intercept, fit.r, fit.min, fit.max})
        out.writeDouble(d);
      out.writeInt(3);
      for (int i = 0; i < 3; i++) {
        out.writeDouble(i);
        out.writeDouble(i);
      }
    }
    check(ResultCodec.decode(old.toByteArray()).origin == DataOrigin.UNKNOWN, "旧记录读取并标记来源未核实");
    if (args.length > 0) CurveExport.write(loaded, Paths.get(args[0]));
    System.out.println("PASS: " + checks + " acquisition regression checks");
  }
}
