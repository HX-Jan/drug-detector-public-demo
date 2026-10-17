package org.example.detector.core;

/** 设备通信接口；适配器必须返回真实设备状态和采样结果。 */
public interface Workstation {
  void connect();

  void disconnect();

  boolean connected();

  Signal baseline();

  Signal sample(String sampleId, DrugType drug);

  /** 标准样品采集；设备适配器仍读取实际信号，模拟器可使用已知浓度。 */
  default Signal sampleStandard(double concentration, DrugType drug) {
    return sample("curve-" + concentration, drug);
  }
}
