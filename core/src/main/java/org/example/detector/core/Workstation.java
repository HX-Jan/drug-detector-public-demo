package org.example.detector.core;

/** Public transport boundary. No proprietary BLE/USB commands are present. */
public interface Workstation {
  void connect();

  void disconnect();

  boolean connected();

  Signal baseline();

  Signal sample(String sampleId, DrugType drug);
}
