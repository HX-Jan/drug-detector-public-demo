package org.example.detector.core;

/** 原有四类演示模型；英文枚举名用于稳定存储，中文名称用于界面显示。 */
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
