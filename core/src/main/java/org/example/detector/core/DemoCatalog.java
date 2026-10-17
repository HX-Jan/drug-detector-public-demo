package org.example.detector.core;

import java.io.IOException;

/** 展示版内置数据目录：不代表真实厂家标定或真实样品性能。 */
public final class DemoCatalog {
  private DemoCatalog() {}

  /** 首次启动时写入一条可直接用于完整向导的药物模板。 */
  public static void ensure(CurveLibrary library) throws IOException {
    for (StandardCurve curve : library.all())
      if (curve.id.equals(defaultCurve().id))
        return;
    library.save(defaultCurve());
  }

  public static StandardCurve defaultCurve() {
    StandardCurve c = new StandardCurve();
    c.id = "DEMO-METH-CURVE";
    c.name = "DEMO-METH";
    c.drug = DrugType.METHAMPHETAMINE;
    c.scanStart = -500;
    c.scanEnd = 800;
    c.peakStart = 250;
    c.peakEnd = 500;
    c.step = 5;
    c.rate = 100;
    c.cycles = 1;
    c.startPotential = 25;
    c.endPotential = 800;
    c.activation = new SimulatedWorkstationSignal().signal();
    c.confirmBaseline(new double[]{1, 1, 1});
    for (double concentration : new double[]{1, 10, 25, 50, 100}) {
      double peak = 1 + 0.2 + 0.08 * concentration;
      c.addPoint(concentration, new double[]{peak, peak, peak});
    }
    c.calculateLod(new double[]{1, 1.2, 0.9, 1.1, 1.05, 0.95, 1.15, 1.0, 1.08, 0.97});
    return c;
  }

  /** 独立生成一条与模拟设备相同采样密度的活化响应曲线。 */
  private static final class SimulatedWorkstationSignal {
    Signal signal() {
      double[] voltage = new double[201], current = new double[201];
      for (int i = 0; i < voltage.length; i++) {
        voltage[i] = -500 + i * 6.5;
        current[i] = 1 + .15 * Math.exp(-Math.pow((voltage[i] - 375) / 50, 2));
      }
      return new Signal(voltage, current);
    }
  }
}
