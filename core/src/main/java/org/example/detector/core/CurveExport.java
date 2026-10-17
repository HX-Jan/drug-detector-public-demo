package org.example.detector.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 输出可检查的 JSON 与真正的 Excel 文件，不用成功提示代替文件写入。 */
public final class CurveExport {
  private CurveExport() {}

  public static String json(StandardCurve c) {
    c.validateSave();
    Calibration f = c.fit();
    StringBuilder s =
        new StringBuilder("{\n  \"source\":\"")
            .append(c.origin.name())
            .append(
                "\",\n  \"version\":2,\n  \"voltageUnit\":\"mV\",\n  \"activationVoltageUnit\":\"")
            .append(c.activationUnitVerified ? "mV" : "UNKNOWN")
            .append("\",\n  \"id\":\"")
            .append(c.id)
            .append("\",\n  \"name\":\"")
            .append(c.name)
            .append("\",\n  \"drug\":\"")
            .append(c.drug.name())
            .append("\",\n  \"unit\":\"pg/mL\",\n");
    String[] names = {
      "scanStart",
      "scanEnd",
      "peakStart",
      "peakEnd",
      "step",
      "rate",
      "cycles",
      "hold",
      "quiet",
      "startPotential",
      "endPotential",
      "slope",
      "intercept",
      "r",
      "lod",
      "loq"
    };
    double[] values = {
      c.scanStart,
      c.scanEnd,
      c.peakStart,
      c.peakEnd,
      c.step,
      c.rate,
      c.cycles,
      c.hold,
      c.quiet,
      c.startPotential,
      c.endPotential,
      f.slope,
      f.intercept,
      f.r,
      c.lod,
      c.loq
    };
    for (int i = 0; i < names.length; i++)
      s.append("  \"").append(names[i]).append("\":").append(values[i]).append(",\n");
    s.append("  \"automatic\":")
        .append(c.automatic)
        .append(",\n  \"baseline\":")
        .append(Arrays.toString(c.baseline))
        .append(",\n  \"lodSamples\":")
        .append(Arrays.toString(c.lodSamples))
        .append(",\n  \"activation\":");
    signal(s, c.activation, c.activationUnitVerified ? "mV" : "UNKNOWN");
    s.append(",\n  \"baselineSignals\":");
    signals(s, c.baselineSignals);
    s.append(",\n  \"lodSignals\":");
    signals(s, c.lodSignals);
    s.append(",\n  \"points\":[");
    for (int i = 0; i < c.points.size(); i++) {
      if (i > 0) s.append(',');
      StandardCurve.Point p = c.points.get(i);
      s.append("{\"concentration\":")
          .append(p.concentration)
          .append(",\"peaks\":")
          .append(Arrays.toString(p.peaks))
          .append(",\"signal\":");
      signal(s, p.signal);
      s.append(",\"repeats\":");
      signals(s, p.repeats);
      s.append('}');
    }
    return s.append("]\n}\n").toString();
  }

  private static void signal(StringBuilder s, Signal v) {
    signal(s, v, "mV");
  }

  private static void signals(StringBuilder s, Signal[] signals) {
    s.append('[');
    for (int i = 0; i < signals.length; i++) {
      if (i > 0) s.append(',');
      signal(s, signals[i]);
    }
    s.append(']');
  }

  private static void signal(StringBuilder s, Signal v, String unit) {
    if (v == null) s.append("null");
    else
      s.append("{\"")
          .append(unit)
          .append("\":")
          .append(Arrays.toString(v.voltages()))
          .append(",\"uA\":")
          .append(Arrays.toString(v.currents()))
          .append('}');
  }

  public static void xlsx(StandardCurve c, OutputStream out) throws IOException {
    c.validateSave();
    Calibration f = c.fit();
    List<Object[]> summary = new ArrayList<>(), samples = new ArrayList<>();
    summary.add(new Object[] {"数据来源", "名称", "类别", "浓度单位", "斜率", "截距", "r", "LOD", "LOQ", "方式"});
    summary.add(
        new Object[] {
          c.origin.name(),
          c.name,
          c.drug.label,
          "pg/mL",
          f.slope,
          f.intercept,
          f.r,
          c.lod,
          c.loq,
          c.automatic ? "自动计算" : "手动录入"
        });
    summary.add(
        new Object[] {
          "扫描起点 mV", "扫描终点 mV", "寻峰起点 mV", "寻峰终点 mV", "步进 mV", "速率 mV/s", "圈数", "静置 s", "沉积 s",
          "起始电位 mV", "最高电位 mV"
        });
    summary.add(
        new Object[] {
          c.scanStart,
          c.scanEnd,
          c.peakStart,
          c.peakEnd,
          c.step,
          c.rate,
          c.cycles,
          c.quiet,
          c.hold,
          c.startPotential,
          c.endPotential
        });
    samples.add(
        new Object[] {
          "数据来源", "类别", "浓度 pg/mL", "重复序号", "峰电流 μA / 反算浓度 pg/mL", "电位", "电流 μA", "电位单位"
        });
    for (int i = 0; i < c.baseline.length; i++)
      samples.add(new Object[] {c.origin.name(), "基线", 0, i + 1, c.baseline[i]});
    for (int i = 0; i < c.baselineSignals.length; i++)
      traceRows(samples, c.origin, "基线采集", 0, i + 1, c.baselineSignals[i], "mV");
    for (StandardCurve.Point p : c.points) {
      for (int i = 0; i < p.peaks.length; i++)
        samples.add(new Object[] {c.origin.name(), "浓度采集", p.concentration, i + 1, p.peaks[i]});
      traceRows(
          samples,
          c.origin,
          p.repeats.length == 0 ? "旧版峰值示意" : "浓度平均曲线",
          p.concentration,
          0,
          p.signal,
          "mV");
      for (int i = 0; i < p.repeats.length; i++)
        traceRows(samples, c.origin, "浓度原始曲线", p.concentration, i + 1, p.repeats[i], "mV");
    }
    traceRows(
        samples, c.origin, "活化曲线", 0, 1, c.activation, c.activationUnitVerified ? "mV" : "UNKNOWN");
    for (int i = 0; i < c.lodSamples.length; i++)
      samples.add(
          new Object[] {
            c.origin.name(), "LOD 反算值", c.points.get(0).concentration, i + 1, c.lodSamples[i]
          });
    for (int i = 0; i < c.lodSignals.length; i++)
      traceRows(
          samples,
          c.origin,
          "LOD 原始曲线",
          c.points.get(0).concentration,
          i + 1,
          c.lodSignals[i],
          "mV");
    XlsxExporter.tables(summary, samples, out);
  }

  private static void traceRows(
      List<Object[]> rows,
      DataOrigin origin,
      String name,
      double c,
      int repeat,
      Signal signal,
      String unit) {
    if (signal == null) return;
    double[] x = signal.voltages(), y = signal.currents();
    for (int i = 0; i < x.length; i++)
      rows.add(new Object[] {origin.name(), name, c, repeat, "", x[i], y[i], unit});
  }

  /** 每次导出使用唯一文件名，防止无提示覆盖原有成果。 */
  public static Path write(StandardCurve c, Path directory) throws IOException {
    c.validateSave();
    Files.createDirectories(directory);
    String name =
        c.name
            + "-"
            + System.currentTimeMillis()
            + "-"
            + UUID.randomUUID().toString().substring(0, 8);
    Path json = directory.resolve(name + ".json"), excel = directory.resolve(name + ".xlsx");
    try {
      Files.write(json, json(c).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
      try (OutputStream out = Files.newOutputStream(excel, StandardOpenOption.CREATE_NEW)) {
        xlsx(c, out);
      }
    } catch (IOException e) {
      Files.deleteIfExists(json);
      Files.deleteIfExists(excel);
      throw e;
    }
    return excel;
  }
}
