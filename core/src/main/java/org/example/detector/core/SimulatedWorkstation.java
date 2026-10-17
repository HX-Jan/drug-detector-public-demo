package org.example.detector.core;

/** 内置基准信号源：在真实硬件适配器接入前，用于校验仪器链路与操作流程。 */
public final class SimulatedWorkstation implements Workstation {
  @Override
  public DataOrigin origin() {
    return DataOrigin.SIMULATED;
  }

  private volatile boolean connected;

  public void connect() {
    connected = true;
  }

  public void disconnect() {
    connected = false;
  }

  public boolean connected() {
    return connected;
  }

  public Signal baseline() {
    requireConnection();
    return trace(0);
  }

  public Signal sample(String sampleId, DrugType drug) {
    requireConnection();
    // 同一编号始终生成相同的基准浓度，便于复核设备读数一致性。
    double syntheticConcentration = 5 + Math.floorMod(sampleId.hashCode(), 8500) / 100.0;
    return trace(0.08 * syntheticConcentration + 0.2);
  }

  @Override
  public Signal sampleStandard(double concentration, DrugType drug) {
    requireConnection();
    if (!Double.isFinite(concentration) || concentration <= 0)
      throw new IllegalArgumentException("标准样品浓度必须为有限正数");
    // 标曲使用操作者输入的已知浓度，保持与内置样本相同的线性响应模型。
    return trace(0.08 * concentration + 0.2);
  }

  private void requireConnection() {
    if (!connected) throw new IllegalStateException("基准信号源未连接");
  }

  @Override
  public Signal activation(StandardCurve c) {
    requireConnection();
    c.validateActivation();
    double low = Math.min(c.startPotential, c.endPotential),
        high = Math.max(c.startPotential, c.endPotential);
    int count = pointCount(low, high, c.step);
    double[] v = new double[count], i = new double[count];
    for (int n = 0; n < count; n++) {
      double t = n / (double) (count - 1);
      v[n] = (low + (high - low) * t) / 1000;
      i[n] =
          1
              + (0.15 + 0.001 * c.cycles) * Math.sin(2 * Math.PI * t)
              + 0.0001 * c.rate * t
              + 0.0001 * (c.hold - c.quiet);
    }
    return new Signal(v, i);
  }

  @Override
  public Signal baseline(StandardCurve c, int repetition) {
    requireConnection();
    return configuredTrace(c, 0, (repetition - 1) * 0.0002);
  }

  @Override
  public Signal sampleStandard(
      double concentration, StandardCurve c, int repetition, int repetitions) {
    requireConnection();
    if (!Double.isFinite(concentration) || concentration <= 0)
      throw new IllegalArgumentException("标准浓度无效");
    // 确定性重复偏差只由模拟器生成，工作流不构造采集电流。
    return configuredTrace(
        c, 0.08 * concentration + 0.2, (repetition - (repetitions - 1) / 2.0) * 0.0007);
  }

  private Signal configuredTrace(StandardCurve c, double delta, double variation) {
    c.validateInfo();
    int count = pointCount(c.scanStart, c.scanEnd, c.step);
    double[] v = new double[count], i = new double[count];
    double center = (c.peakStart + c.peakEnd) / 2,
        width = Math.max(1, (c.peakEnd - c.peakStart) / 5);
    for (int n = 0; n < count; n++) {
      double mv = c.scanStart + (c.scanEnd - c.scanStart) * n / (count - 1);
      v[n] = mv / 1000;
      i[n] = 1 + variation + delta * Math.exp(-Math.pow((mv - center) / width, 2));
    }
    return new Signal(v, i);
  }

  private static int pointCount(double start, double end, double step) {
    if (!Double.isFinite(step) || step <= 0) throw new IllegalArgumentException("步进必须为有限正数");
    double count = Math.ceil((end - start) / step) + 1;
    if (!Double.isFinite(count) || count > 10000)
      throw new IllegalArgumentException("采样点超过 10000，请增大步进");
    return Math.max(3, (int) count);
  }

  private Signal trace(double delta) {
    double[] v = new double[101], c = new double[101];
    for (int i = 0; i < 101; i++) {
      v[i] = -0.5 + i * 0.01;
      c[i] = 1 + delta * Math.exp(-Math.pow(v[i] / 0.09, 2));
    }
    return new Signal(v, c);
  }
}
