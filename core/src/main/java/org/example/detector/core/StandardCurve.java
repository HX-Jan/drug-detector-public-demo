package org.example.detector.core;

import java.util.*;

/**
 * 五步标曲向导的数据草稿。电位使用 mV，电流使用 μA，浓度使用 pg/mL。 1 pg/mL = 1 ng/L，因此交给原有检测计算时无需改变数值。
 * 修改上游参数时主动清除下游结果，避免保存过期统计值。
 */
public final class StandardCurve {
  public String id = UUID.randomUUID().toString();
  public String name = "";
  public DrugType drug = DrugType.METHAMPHETAMINE;
  public double scanStart = 0, scanEnd = 0, peakStart = 0, peakEnd = 0;
  public double step = 5, rate = 100, cycles = 1, hold = 0, quiet = 0, startPotential = 25,
      endPotential = 800;
  public Signal activation;
  public double[] baseline;
  public final List<Point> points = new ArrayList<>();
  public boolean automatic;
  public double[] lodSamples = new double[0];
  public double mean = Double.NaN, sd = Double.NaN, lod = Double.NaN, loq = Double.NaN;

  /** 每个浓度保留三次峰电流及实际用于绘图、导出的平均信号。 */
  public static final class Point {
    public final double concentration;
    public final double[] peaks;
    public final Signal signal;
    public Point(double concentration, double[] peaks, Signal signal) {
      this.concentration = concentration;
      this.peaks = peaks.clone();
      this.signal = signal;
    }
    public double peak() {
      return Arrays.stream(peaks).average().orElseThrow(IllegalStateException::new);
    }
  }

  public void validateInfo() {
    if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_-]{0,23}"))
      throw new IllegalArgumentException("英文名须以字母开头，限 1–24 位字母、数字、下划线或短横线");
    finite(scanStart, scanEnd, peakStart, peakEnd);
    if (scanStart >= scanEnd || peakStart >= peakEnd || peakStart < scanStart || peakEnd > scanEnd)
      throw new IllegalArgumentException("扫描起点须小于终点，寻峰范围须位于扫描范围内");
    if (Math.abs(scanStart) > 5000 || Math.abs(scanEnd) > 5000)
      throw new IllegalArgumentException("扫描电位限 ±5000 mV");
  }

  public void validateActivation() {
    finite(step, rate, cycles, hold, quiet, startPotential, endPotential);
    if (step <= 0 || rate <= 0 || cycles < 1 || cycles > 100 || cycles != Math.rint(cycles)
        || hold < 0 || quiet < 0 || startPotential == endPotential
        || Math.abs(startPotential) > 5000 || Math.abs(endPotential) > 5000)
      throw new IllegalArgumentException("活化参数无效：步进、速率须为正数，圈数为 1–100 整数，时间非负，起止电位不同且在 ±5000 mV 内");
  }

  public void invalidateAcquisition() {
    activation = null;
    baseline = null;
    points.clear();
    invalidateLod();
  }
  public void invalidateLod() {
    lodSamples = new double[0];
    mean = sd = lod = loq = Double.NaN;
  }
  public double baselinePeak() {
    if (baseline == null)
      throw new IllegalStateException("请先采集并确认基线");
    return Arrays.stream(baseline).average().orElseThrow(IllegalStateException::new);
  }
  public void confirmBaseline(double[] peaks) {
    validatePeaks(peaks);
    baseline = peaks.clone();
    points.clear();
    invalidateLod();
  }
  public void addPoint(double concentration, double[] peaks) {
    baselinePeak();
    validatePeaks(peaks);
    finite(concentration);
    if (concentration <= 0 || points.size() >= 10)
      throw new IllegalArgumentException("需要正浓度，最多采集 10 个浓度点");
    for (Point p : points)
      if (p.concentration == concentration)
        throw new IllegalArgumentException("该浓度已存在，请先删除旧点再采集");
    double peak = Arrays.stream(peaks).average().orElseThrow(IllegalStateException::new);
    points.add(new Point(concentration, peaks, trace(peak)));
    points.sort(Comparator.comparingDouble(p -> p.concentration));
    invalidateLod();
  }
  public void removePoint(int index) {
    points.remove(index);
    invalidateLod();
  }

  /** 向导按每个浓度的平均峰值减去确认基线拟合，与旧版相邻差值接口分开。 */
  public Calibration fit() {
    if (points.size() < 2)
      throw new IllegalStateException("至少采集两个不同浓度点后再继续");
    double mx = 0, my = 0;
    for (Point p : points) {
      mx += p.concentration / points.size();
      my += (p.peak() - baselinePeak()) / points.size();
    }
    double xx = 0, yy = 0, xy = 0;
    for (Point p : points) {
      double x = p.concentration - mx, y = p.peak() - baselinePeak() - my;
      xx += x * x;
      yy += y * y;
      xy += x * y;
    }
    if (xx < 1e-12 || yy < 1e-12)
      throw new IllegalStateException("标定数据变化不足，无法拟合");
    double k = xy / xx;
    return new Calibration(id, drug, k, my - k * mx,
        Math.max(-1, Math.min(1, xy / Math.sqrt(xx * yy))), points.get(0).concentration,
        points.get(points.size() - 1).concentration);
  }

  public void manualLod(double value) {
    finite(value);
    if (value <= 0 || !Double.isFinite(value * 4))
      throw new IllegalArgumentException("LOD 必须为有限正数");
    invalidateLod();
    automatic = false;
    lod = value;
    loq = 4 * value;
  }
  /** 截图给定公式：十次浓度反算值的样本标准差，分母 n−1，而非总体标准差。 */
  public void calculateLod(double[] values) {
    fit();
    if (values.length != 10)
      throw new IllegalArgumentException("自动 LOD 必须完成 10 次采集");
    finite(values);
    double m = Arrays.stream(values).average().orElseThrow(IllegalStateException::new), sum = 0;
    for (double c : values)
      sum += (c - m) * (c - m);
    double s = Math.sqrt(sum / 9.0);
    finite(m, s, 2.82 * s, 4 * 2.82 * s);
    automatic = true;
    lodSamples = values.clone();
    mean = m;
    sd = s;
    lod = 2.82 * s;
    loq = 4 * lod;
  }
  public void validateSave() {
    validateInfo();
    validateActivation();
    fit();
    if (!Double.isFinite(lod) || !Double.isFinite(loq))
      throw new IllegalStateException("请录入 LOD 或完成自动计算");
  }
  private static void validatePeaks(double[] peaks) {
    if (peaks == null || peaks.length != 3)
      throw new IllegalArgumentException("每点须完成 3 次重复采集");
    finite(peaks);
  }
  public static void finite(double... values) {
    for (double d : values)
      if (!Double.isFinite(d))
        throw new IllegalArgumentException("参数须为有限数值");
  }
  /** 将键盘输入统一转换为有限数值，让两端给出相同的中文错误提示。 */
  public static double parseNumber(String value) {
    try {
      double d = Double.parseDouble(value.trim());
      finite(d);
      return d;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("请输入有效数值，可使用小数点和负号");
    }
  }

  /** 从已保存采样值生成绘图轨迹；不得用该方法代替设备采集。 */
  public Signal trace(double peak) {
    double[] x = new double[201], y = new double[201];
    double center = (peakStart + peakEnd) / 2, width = Math.max(1, (peakEnd - peakStart) / 5);
    for (int i = 0; i < x.length; i++) {
      x[i] = scanStart + (scanEnd - scanStart) * i / 200;
      y[i] = peak * Math.exp(-Math.pow((x[i] - center) / width, 2));
    }
    return new Signal(x, y);
  }
  public String statistics() {
    return String.format(Locale.CHINA,
        "均值 C̄     %s pg/mL\n标准差 Sc     %s pg/mL\n检出限 LOD     %s pg/mL\n定量限 LOQ     %s pg/mL",
        number(mean), number(sd), number(lod), number(loq));
  }
  public String lodDetails() {
    StringBuilder s = new StringBuilder(
        "【计算依据与公式】\n1. 均值 C̄ = ΣCi / 10\n2. 标准差 Sc = √[Σ(Ci − C̄)² / 9]\n3. 检出限 LOD = 2.82 × Sc\n4. 定量限 LOQ = 4.0 × LOD\n\n【10 次低浓度反算值（pg/mL）】\n");
    for (int i = 0; i < lodSamples.length; i++)
      s.append("C").append(i + 1).append(": ").append(number(lodSamples[i])).append('\n');
    return s.append("\n【统计与计算结果】\n").append(statistics())
        .toString();
  }
  public static String number(double d) {
    return Double.isFinite(d) ? String.format(Locale.ROOT, "%.4f", d) : "未计算";
  }
  @Override
  public String toString() {
    return name;
  }
}
