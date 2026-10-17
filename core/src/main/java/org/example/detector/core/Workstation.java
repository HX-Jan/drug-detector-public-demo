package org.example.detector.core;

/** 设备通信接口；适配器必须返回真实设备状态和采样结果。 */
public interface Workstation {
  void connect();

  void disconnect();

  boolean connected();

  Signal baseline();

  Signal sample(String sampleId, DrugType drug);
}
