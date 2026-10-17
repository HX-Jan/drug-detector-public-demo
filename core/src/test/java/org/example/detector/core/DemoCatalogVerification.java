package org.example.detector.core;

import java.nio.file.Files;

/** 展示版启动契约：无真实设备时也必须能完成模拟采集。 */
public final class DemoCatalogVerification {
  public static void main(String[] args) throws Exception {
    CurveLibrary library = new CurveLibrary(Files.createTempDirectory("demo-catalog-").resolve("curves"));
    DemoCatalog.ensure(library);
    StandardCurve curve = library.all().stream().findFirst().orElseThrow();
    if (!curve.name.equals("DEMO-METH") || curve.drug != DrugType.METHAMPHETAMINE
        || curve.points.size() < 2 || curve.activation == null || !Double.isFinite(curve.lod))
      throw new AssertionError("内置模板不完整");
    SimulatedWorkstation device = new SimulatedWorkstation();
    device.connect();
    if (device.sample(DemoSample.standard().sampleId(), curve.drug).peak() <= device.baseline().peak())
      throw new AssertionError("内置模拟设备未产生响应曲线");
    System.out.println("PASS: 内置模拟设备、药物模板和响应曲线");
  }
}
