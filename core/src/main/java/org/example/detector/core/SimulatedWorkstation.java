package org.example.detector.core;

/** 内置基准信号源：在真实硬件适配器接入前，用于校验仪器链路与操作流程。 */
public final class SimulatedWorkstation implements Workstation {
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

  private void requireConnection() {
    if (!connected)
      throw new IllegalStateException("基准信号源未连接");
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
