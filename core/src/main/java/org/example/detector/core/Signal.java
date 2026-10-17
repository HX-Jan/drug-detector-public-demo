package org.example.detector.core;

/** 不可变采样序列；构造和读取均复制数组，防止绘图或导出修改历史数据。 */
public final class Signal {
  private final double[] voltage, current;

  public Signal(double[] voltage, double[] current) {
    if (voltage.length < 3 || voltage.length != current.length)
      throw new IllegalArgumentException("信号至少需要三个匹配采样点");
    for (int i = 0; i < voltage.length; i++) {
      if (!Double.isFinite(voltage[i]) || !Double.isFinite(current[i])
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

  /**
   * 当前仅取正向最大电流。真实 DPV/SWV 的负向峰、基线漂移等需要结合硬件另行验证。
   */
  public double peak() {
    double max = -Double.MAX_VALUE;
    for (double i : current)
      max = Math.max(max, i);
    return max;
  }
}
