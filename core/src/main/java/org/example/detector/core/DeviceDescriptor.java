package org.example.detector.core;

/** 由实际设备适配器发现的设备描述。 */
public record DeviceDescriptor(String id, String name, String transport, String address) {
  public DeviceDescriptor {
    if (id == null || id.isBlank() || name == null || name.isBlank()
        || transport == null || transport.isBlank())
      throw new IllegalArgumentException("设备信息不完整");
  }
}
