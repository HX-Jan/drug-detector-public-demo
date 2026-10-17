package org.example.detector.core;

import java.nio.file.*;

/** 正式数据边界的独立验收入口。 */
public final class ProductionVerification {
  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("detector-production-");
    CurveLibrary library = new CurveLibrary(root.resolve("curves"));
    if (!library.all().isEmpty()) throw new AssertionError("新库不得生成预置标曲");
    if (new UnavailableWorkstation().connected()) throw new AssertionError("未配置设备不得显示已连接");
    DemoSample sample = DemoSample.standard();
    if (!sample.sampleId().equals("DEMO-METH-001") || sample.drug() != DrugType.METHAMPHETAMINE)
      throw new AssertionError("内置测试样本定义不稳定");
    SimulatedWorkstation simulated = new SimulatedWorkstation();
    simulated.connect();
    if (simulated.sample(sample.sampleId(), sample.drug()).currents().length != 101)
      throw new AssertionError("内置测试样本未生成模拟信号");
    try { new UnavailableWorkstation().baseline(); throw new AssertionError("未配置设备不得采集"); }
    catch (IllegalStateException expected) { }
    System.out.println("PASS: 正式数据边界");
  }
}
