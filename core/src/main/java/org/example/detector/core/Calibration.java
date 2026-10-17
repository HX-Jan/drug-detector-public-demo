package org.example.detector.core;

import java.util.Locale;
import java.util.UUID;

/** 线性标曲 y=kC+b。浓度单位 ng/L（数值等同 pg/mL），电流单位 μA。 */
public final class Calibration {
  public final String id;
  public final DrugType drug;
  public final double slope, intercept, r, min, max;

  public Calibration(String id, DrugType drug, double slope, double intercept, double r, double min,
      double max) {
    if (id == null || drug == null || !Double.isFinite(slope) || slope <= 1e-12
        || !Double.isFinite(intercept) || !Double.isFinite(r) || r < -1 || r > 1
        || !Double.isFinite(min) || !Double.isFinite(max) || min < 0 || max <= min)
      throw new IllegalArgumentException("标曲参数无效");
    this.id = id;
    this.drug = drug;
    this.slope = slope;
    this.intercept = intercept;
    this.r = r;
    this.min = min;
    this.max = max;
  }

  /** 保留旧版相邻电流差拟合接口；新向导使用 StandardCurve.fit 的固定基线差。 */
  public static Calibration fit(DrugType drug, double[] concentration, double[] cumulativePeaks) {
    int n = concentration.length;
    if (n < 5 || n > 10 || cumulativePeaks.length != n + 1)
      throw new IllegalArgumentException("需要 5–10 个浓度及 I0 到 In");
    double[] y = new double[n];
    double mx = 0, my = 0;
    for (double p : cumulativePeaks)
      if (!Double.isFinite(p))
        throw new IllegalArgumentException("峰电流必须为有限数值");
    for (int i = 0; i < n; i++) {
      double x = concentration[i];
      if (!Double.isFinite(x) || x <= 0 || (i > 0 && x <= concentration[i - 1]))
        throw new IllegalArgumentException("浓度必须为严格递增的正数");
      y[i] = cumulativePeaks[i + 1] - cumulativePeaks[i];
      mx += x / n;
      my += y[i] / n;
    }
    double xx = 0, yy = 0, xy = 0;
    for (int i = 0; i < n; i++) {
      double dx = concentration[i] - mx, dy = y[i] - my;
      xx += dx * dx;
      yy += dy * dy;
      xy += dx * dy;
    }
    if (xx < 1e-12 || yy < 1e-12)
      throw new IllegalArgumentException("标定点缺乏有效变化");
    double k = xy / xx;
    return new Calibration("FIT-" + UUID.randomUUID(), drug, k, my - k * mx,
        Math.max(-1, Math.min(1, xy / Math.sqrt(xx * yy))), concentration[0], concentration[n - 1]);
  }

  public double concentration(double delta) {
    // 不截断负数或区间外值，让调用方能够如实显示超出标定范围的状态。
    if (!Double.isFinite(delta))
      throw new IllegalArgumentException("电流差值无效");
    double result = (delta - intercept) / slope;
    if (!Double.isFinite(result))
      throw new IllegalArgumentException("浓度计算溢出");
    return result;
  }

  public boolean inRange(double x) {
    return x >= min && x <= max;
  }

  public Signal fittedLine() {
    double[] x = new double[51], y = new double[51];
    for (int i = 0; i < x.length; i++) {
      x[i] = min + (max - min) * i / (x.length - 1);
      y[i] = slope * x[i] + intercept;
    }
    return new Signal(x, y);
  }

  public String equation() {
    return String.format(Locale.ROOT, "ΔI = %.5f × C %+.5f μA · r = %.6f", slope, intercept, r);
  }

  public static Calibration demo(DrugType drug) {
    throw new IllegalStateException("检测项目尚未配置有效标准曲线");
  }
}
