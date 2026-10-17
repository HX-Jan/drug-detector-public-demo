package org.example.detector.core;

/** 数据来源随记录持久保存并进入导出文件。 */
public enum DataOrigin {
  SIMULATED("模拟数据"),
  DEVICE("设备采集"),
  UNKNOWN("来源未标记");
  public final String label;

  DataOrigin(String label) {
    this.label = label;
  }

  /** 模拟标曲或模拟采集参与计算时，结果不能归为设备实测。 */
  public static DataOrigin combine(DataOrigin a, DataOrigin b) {
    if (a == SIMULATED || b == SIMULATED) return SIMULATED;
    return a == DEVICE && b == DEVICE ? DEVICE : UNKNOWN;
  }
}
