package org.example.detector.core;

/** 不可变检测结果，保存当次信号与标曲快照；drug 是操作员选择的类型，不是识别结论。 */
public final class DetectionResult {
  public final String id, sampleId;
  public final DrugType drug;
  public final long epochMillis;
  public final double baselinePeak, samplePeak, concentration;
  public final Calibration calibration;
  public final Signal signal;

  public DetectionResult(
      String id,
      String sampleId,
      DrugType drug,
      long epochMillis,
      double baselinePeak,
      double samplePeak,
      Calibration calibration,
      Signal signal) {
    if (id == null
        || id.isEmpty()
        || sampleId == null
        || !sampleId.matches("[A-Za-z0-9_-]{1,40}")
        || drug == null
        || calibration == null
        || calibration.drug != drug
        || signal == null
        || epochMillis < 0
        || !Double.isFinite(baselinePeak)
        || !Double.isFinite(samplePeak)) throw new IllegalArgumentException("检测记录无效");
    this.id = id;
    this.sampleId = sampleId;
    this.drug = drug;
    this.epochMillis = epochMillis;
    this.baselinePeak = baselinePeak;
    this.samplePeak = samplePeak;
    this.calibration = calibration;
    this.signal = signal;
    concentration = calibration.concentration(samplePeak - baselinePeak);
  }

  public String status() {
    return calibration.inRange(concentration) ? "待判定（标曲范围内）" : "超出标曲范围";
  }
}
