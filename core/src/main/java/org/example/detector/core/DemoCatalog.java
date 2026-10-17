package org.example.detector.core;

import java.io.IOException;
import java.util.*;

/** 展示版内置数据目录：不代表真实厂家标定或真实样品性能。 */
public final class DemoCatalog {
  private DemoCatalog() {}

  /** 首次启动写入四类合成模板；用户删除后不自动恢复。 */
  public static void ensure(CurveLibrary library) throws IOException {
    List<StandardCurve> templates = new ArrayList<>();
    for (DrugType drug : DrugType.values()) templates.add(template(drug));
    library.initializeIfAbsent(templates);
  }

  public static StandardCurve defaultCurve() {
    return template(DrugType.METHAMPHETAMINE);
  }

  private static StandardCurve template(DrugType drug) {
    StandardCurve c = new StandardCurve();
    String code = new String[] {"METH", "MORPHINE", "TRAMADOL", "METHADONE"}[drug.ordinal()];
    c.id = "DEMO-" + code + "-CURVE";
    c.name = "DEMO-" + code;
    c.drug = drug;
    c.origin = DataOrigin.SIMULATED;
    c.scanStart = -500;
    c.scanEnd = 800;
    c.peakStart = 250;
    c.peakEnd = 500;
    c.step = 5;
    c.rate = 100;
    c.cycles = 1;
    c.startPotential = 25;
    c.endPotential = 800;
    SimulatedWorkstation device = new SimulatedWorkstation();
    device.connect();
    c.activation = device.activation(c).toMillivolts();
    c.activationUnitVerified = true;
    Signal[] baseline = new Signal[3];
    for (int i = 0; i < 3; i++) baseline[i] = device.baseline(c, i).toMillivolts();
    c.confirmBaseline(baseline);
    for (double concentration : new double[] {1, 10, 25, 50, 100}) {
      Signal[] signals = new Signal[3];
      for (int i = 0; i < 3; i++)
        signals[i] = device.sampleStandard(concentration, c, i, 3).toMillivolts();
      c.addPoint(concentration, signals);
    }
    Signal[] signals = new Signal[10];
    double[] values = new double[10];
    for (int i = 0; i < 10; i++) {
      signals[i] = device.sampleStandard(1, c, i, 10).toMillivolts();
      values[i] =
          c.fit().concentration(signals[i].peakWithin(c.peakStart, c.peakEnd) - c.baselinePeak());
    }
    c.calculateLod(values);
    c.lodSignals = signals;
    return c;
  }
}
