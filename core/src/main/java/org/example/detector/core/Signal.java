package org.example.detector.core;

/** 不可变采样序列；构造和读取均复制数组，防止绘图或导出修改历史数据。 */
public final class Signal {
  private final double[] voltage, current;

  public Signal(double[] voltage, double[] current) {
    if (voltage.length < 3 || voltage.length != current.length)
      throw new IllegalArgumentException("信号至少需要三个匹配采样点");
    for (int i = 0; i < voltage.length; i++) {
      if (!Double.isFinite(voltage[i])
          || !Double.isFinite(current[i])
          || (i > 0 && voltage[i] <= voltage[i - 1]))
        throw new IllegalArgumentException("采样值必须有限且电位递增");
    }
    this.voltage = voltage.clone();
    this.current = current.clone();
  }

  public double[] voltages() {
    return voltage.clone();
  }

  public double[] currents() {
    return current.clone();
  }

  /** 适配器信号统一使用 V，进入标曲库时显式转换成 mV。 */
  public Signal toMillivolts() {
    double[] scaled = voltages();
    for (int i = 0; i < scaled.length; i++) scaled[i] *= 1000;
    return new Signal(scaled, current);
  }

  public double peakWithin(double low, double high) {
    double peak = -Double.MAX_VALUE;
    for (int i = 0; i < voltage.length; i++)
      if (voltage[i] >= low && voltage[i] <= high) peak = Math.max(peak, current[i]);
    if (peak == -Double.MAX_VALUE) throw new IllegalArgumentException("寻峰区间内没有采样点");
    return peak;
  }

  public static Signal average(Signal[] signals) {
    if (signals.length == 0) throw new IllegalArgumentException("缺少采样数据");
    double[] x = signals[0].voltages(), mean = new double[x.length];
    for (Signal s : signals) {
      if (!java.util.Arrays.equals(x, s.voltage))
        throw new IllegalArgumentException("重复采集的电位网格不一致");
      for (int i = 0; i < mean.length; i++) mean[i] += s.current[i] / signals.length;
    }
    return new Signal(x, mean);
  }

  /** 当前仅取正向最大电流。真实 DPV/SWV 的负向峰、基线漂移等需要结合硬件另行验证。 */
  public double peak() {
    double max = -Double.MAX_VALUE;
    for (double i : current) max = Math.max(max, i);
    return max;
  }
}
