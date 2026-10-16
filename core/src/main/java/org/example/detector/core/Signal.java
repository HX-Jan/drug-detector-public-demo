package org.example.detector.core;

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

  /**
   * Positive peak only. Real signed DPV/SWV preprocessing requires hardware-specific validation.
   */
  public double peak() {
    double max = -Double.MAX_VALUE;
    for (double i : current) max = Math.max(max, i);
    return max;
  }
}
