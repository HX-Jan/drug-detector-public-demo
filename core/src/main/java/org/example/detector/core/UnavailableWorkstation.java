package org.example.detector.core;

import java.util.Collections;
import java.util.List;

/** 未配置设备协议时的明确边界；不会以本地算法伪造设备响应。 */
public final class UnavailableWorkstation implements Workstation {
  @Override public void connect() { throw new IllegalStateException("设备通信尚未配置"); }
  @Override public void disconnect() { }
  @Override public boolean connected() { return false; }
  @Override public Signal baseline() { throw new IllegalStateException("设备通信尚未配置"); }
  @Override public Signal sample(String sampleId, DrugType drug) { throw new IllegalStateException("设备通信尚未配置"); }
  public List<DeviceDescriptor> discover() { return Collections.emptyList(); }
}
