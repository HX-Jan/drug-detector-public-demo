package org.example.detector.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 两端共用的标曲库。单文件原子替换；只有落盘成功后才修改内存列表。 */
public final class CurveLibrary {
  private final Path file;
  private final List<StandardCurve> records = new ArrayList<>();
  private boolean initialized;

  public CurveLibrary(Path directory) throws IOException {
    Files.createDirectories(directory);
    file = directory.resolve("standard-curves.properties");
    if (Files.exists(file)) {
      initialized = true;
      Properties p = new Properties();
      try (InputStream in = Files.newInputStream(file)) {
        p.load(in);
      }
      int count = Integer.parseInt(p.getProperty("count", "0"));
      for (int i = 0; i < count; i++) records.add(decode(p, "c" + i + "."));
    }
  }

  /** 仅在从未建立库文件时初始化；清空或删除模板也是应当保留的用户状态。 */
  public void initializeIfAbsent(List<StandardCurve> templates) throws IOException {
    if (initialized) return;
    List<StandardCurve> next = new ArrayList<>();
    for (StandardCurve curve : templates) {
      curve.validateSave();
      next.add(copy(curve));
    }
    persist(next);
    records.addAll(next);
  }

  /** 返回脱离库内对象的副本，取消修改不会污染已保存记录。 */
  public List<StandardCurve> all() {
    List<StandardCurve> result = new ArrayList<>();
    for (StandardCurve c : records) result.add(copy(c));
    return result;
  }

  public void save(StandardCurve curve) throws IOException {
    curve.validateSave();
    List<StandardCurve> next = all();
    for (StandardCurve c : next)
      if (!c.id.equals(curve.id) && c.name.equalsIgnoreCase(curve.name))
        throw new IllegalArgumentException("英文名已存在，请使用不同名称");
    next.removeIf(c -> c.id.equals(curve.id));
    next.add(copy(curve));
    persist(next);
    records.clear();
    records.addAll(next);
  }

  public void delete(String id) throws IOException {
    List<StandardCurve> next = all();
    next.removeIf(c -> c.id.equals(id));
    persist(next);
    records.clear();
    records.addAll(next);
  }

  private void persist(List<StandardCurve> next) throws IOException {
    Properties p = new Properties();
    p.setProperty("count", String.valueOf(next.size()));
    for (int i = 0; i < next.size(); i++) encode(p, "c" + i + ".", next.get(i));
    Path temp = Files.createTempFile(file.getParent(), "curves-", ".tmp");
    try {
      try (OutputStream out = Files.newOutputStream(temp)) {
        p.store(out, "standard curves v1");
      }
      try {
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
      }
      initialized = true;
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private static StandardCurve copy(StandardCurve c) {
    Properties p = new Properties();
    encode(p, "", c);
    return decode(p, "");
  }

  private static String values(double[] a) {
    return Arrays.toString(a).replace("[", "").replace("]", "").replace(" ", "");
  }

  private static double[] array(String s) {
    return s.isEmpty()
        ? new double[0]
        : Arrays.stream(s.split(",")).mapToDouble(Double::parseDouble).toArray();
  }

  private static void encode(Properties p, String k, StandardCurve c) {
    p.setProperty(k + "id", c.id);
    p.setProperty(k + "name", c.name);
    p.setProperty(k + "drug", c.drug.name());
    p.setProperty(k + "origin", c.origin.name());
    p.setProperty(k + "activationUnitVerified", String.valueOf(c.activationUnitVerified));
    signals(p, k + "baselineSignals.", c.baselineSignals);
    signals(p, k + "lodSignals.", c.lodSignals);
    p.setProperty(
        k + "parameters",
        values(
            new double[] {
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
              c.endPotential
            }));
    p.setProperty(k + "baseline", c.baseline == null ? "" : values(c.baseline));
    p.setProperty(k + "activationX", c.activation == null ? "" : values(c.activation.voltages()));
    p.setProperty(k + "activationY", c.activation == null ? "" : values(c.activation.currents()));
    p.setProperty(k + "pointCount", String.valueOf(c.points.size()));
    for (int i = 0; i < c.points.size(); i++) {
      StandardCurve.Point pt = c.points.get(i);
      p.setProperty(k + "p" + i, pt.concentration + "," + values(pt.peaks));
      signals(p, k + "p" + i + ".repeats.", pt.repeats);
      signals(p, k + "p" + i + ".average.", new Signal[] {pt.signal});
    }
    p.setProperty(k + "auto", String.valueOf(c.automatic));
    p.setProperty(k + "lodSamples", values(c.lodSamples));
    p.setProperty(k + "statistics", values(new double[] {c.mean, c.sd, c.lod, c.loq}));
  }

  private static StandardCurve decode(Properties p, String k) {
    StandardCurve c = new StandardCurve();
    c.id = p.getProperty(k + "id");
    c.name = p.getProperty(k + "name");
    c.drug = DrugType.valueOf(p.getProperty(k + "drug"));
    c.origin = DataOrigin.valueOf(p.getProperty(k + "origin", "UNKNOWN"));
    c.activationUnitVerified =
        Boolean.parseBoolean(p.getProperty(k + "activationUnitVerified", "false"));
    c.baselineSignals = signals(p, k + "baselineSignals.");
    c.lodSignals = signals(p, k + "lodSignals.");
    double[] a = array(p.getProperty(k + "parameters"));
    c.scanStart = a[0];
    c.scanEnd = a[1];
    c.peakStart = a[2];
    c.peakEnd = a[3];
    c.step = a[4];
    c.rate = a[5];
    c.cycles = a[6];
    c.hold = a[7];
    c.quiet = a[8];
    c.startPotential = a[9];
    c.endPotential = a[10];
    double[] baseline = array(p.getProperty(k + "baseline"));
    c.baseline = baseline.length == 0 ? null : baseline;
    double[] ax = array(p.getProperty(k + "activationX", "")),
        ay = array(p.getProperty(k + "activationY", ""));
    if (ax.length > 0) c.activation = new Signal(ax, ay);
    for (int i = 0; i < Integer.parseInt(p.getProperty(k + "pointCount")); i++) {
      double[] pt = array(p.getProperty(k + "p" + i));
      Signal[] average = signals(p, k + "p" + i + ".average.");
      c.points.add(
          new StandardCurve.Point(
              pt[0],
              Arrays.copyOfRange(pt, 1, 4),
              average.length > 0
                  ? average[0]
                  : c.trace(
                      Arrays.stream(pt, 1, 4).average().orElseThrow(IllegalStateException::new)),
              signals(p, k + "p" + i + ".repeats.")));
    }
    c.automatic = Boolean.parseBoolean(p.getProperty(k + "auto"));
    c.lodSamples = array(p.getProperty(k + "lodSamples"));
    double[] s = array(p.getProperty(k + "statistics"));
    c.mean = s[0];
    c.sd = s[1];
    c.lod = s[2];
    c.loq = s[3];
    return c;
  }

  private static void signals(Properties p, String key, Signal[] signals) {
    p.setProperty(key + "count", String.valueOf(signals.length));
    for (int i = 0; i < signals.length; i++) {
      p.setProperty(key + i + ".x", values(signals[i].voltages()));
      p.setProperty(key + i + ".y", values(signals[i].currents()));
    }
  }

  private static Signal[] signals(Properties p, String key) {
    Signal[] result = new Signal[Integer.parseInt(p.getProperty(key + "count", "0"))];
    for (int i = 0; i < result.length; i++)
      result[i] =
          new Signal(array(p.getProperty(key + i + ".x")), array(p.getProperty(key + i + ".y")));
    return result;
  }
}
