package org.example.detector.core;

/** 设备通信接口；适配器必须返回真实设备状态和采样结果。 */
public interface Workstation {
  default DataOrigin origin() {
    return DataOrigin.UNKNOWN;
  }

  void connect();

  void disconnect();

  boolean connected();

  Signal baseline();

  Signal sample(String sampleId, DrugType drug);

  /** 标准样品采集；设备适配器仍读取实际信号，模拟器可使用已知浓度。 */
  default Signal sampleStandard(double concentration, DrugType drug) {
    return sample("curve-" + concentration, drug);
  }

  /** 所有适配器返回 V；未实现的配置化采集必须明确报错，不能静默忽略参数。 */
  default Signal activation(StandardCurve parameters) {
    throw new UnsupportedOperationException("设备适配器尚未实现活化扫描");
  }

  default Signal baseline(StandardCurve parameters, int repetition) {
    throw new UnsupportedOperationException("设备适配器尚未实现配置化基线采集");
  }

  default Signal sampleStandard(
      double concentration, StandardCurve parameters, int repetition, int repetitions) {
    throw new UnsupportedOperationException("设备适配器尚未实现配置化标准样品采集");
  }
}
