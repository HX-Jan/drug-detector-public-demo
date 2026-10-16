package org.example.detector.core;

/** Reproducible synthetic Gaussian traces. This is not a drug identification algorithm. */
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
    double syntheticConcentration = 5 + Math.floorMod(sampleId.hashCode(), 8500) / 100.0;
    Calibration fixture = Calibration.demo(drug);
    return trace(fixture.slope * syntheticConcentration + fixture.intercept);
  }

  private void requireConnection() {
    if (!connected) throw new IllegalStateException("模拟工作站未连接");
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
