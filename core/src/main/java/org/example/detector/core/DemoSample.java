package org.example.detector.core;

/** 内置演示样本；仅用于模拟设备和端到端界面验收。 */
public record DemoSample(String sampleId, DrugType drug, String description) {
  public static DemoSample standard() {
    return new DemoSample("DEMO-METH-001", DrugType.METHAMPHETAMINE,
        "内置合成样本，用于验证完整检测交互");
  }
}
