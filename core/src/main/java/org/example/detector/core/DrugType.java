package org.example.detector.core;

public enum DrugType {
  METHAMPHETAMINE("甲基苯丙胺"),
  MORPHINE("吗啡"),
  TRAMADOL("曲马多"),
  METHADONE("美沙酮");
  public final String label;

  DrugType(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}
