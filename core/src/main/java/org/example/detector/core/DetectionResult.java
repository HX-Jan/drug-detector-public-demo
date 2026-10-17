package org.example.detector.core;

/** 不可变检测结果，保存当次信号与标曲快照；drug 是操作员选择的类型，不是识别结论。 */
public final class DetectionResult {
  public final String id, sampleId;
  public final DrugType drug;
  public final long epochMillis;
  public final double baselinePeak, samplePeak, concentration;
  public final Calibration calibration;
  public final Signal signal;
  public final DataOrigin origin;
  public final AiReport ai;

  public DetectionResult(
      String id,
      String sampleId,
      DrugType drug,
      long epochMillis,
      double baselinePeak,
      double samplePeak,
      Calibration calibration,
      Signal signal) {
    this(
        id,
        sampleId,
        drug,
        epochMillis,
        baselinePeak,
        samplePeak,
        calibration,
        signal,
        DataOrigin.UNKNOWN);
  }

  public DetectionResult(
      String id,
      String sampleId,
      DrugType drug,
      long epochMillis,
      double baselinePeak,
      double samplePeak,
      Calibration calibration,
      Signal signal,
      DataOrigin origin) {
    this(
        id,
        sampleId,
        drug,
        epochMillis,
        baselinePeak,
        samplePeak,
        calibration,
        signal,
        origin,
        null);
  }

  public DetectionResult(
      String id,
      String sampleId,
      DrugType drug,
      long epochMillis,
      double baselinePeak,
      double samplePeak,
      Calibration calibration,
      Signal signal,
      DataOrigin origin,
      AiReport ai) {
    if (id == null
        || id.isEmpty()
        || sampleId == null
        || !sampleId.matches("[A-Za-z0-9_-]{1,40}")
        || drug == null
        || calibration == null
        || calibration.drug != drug
        || signal == null
        || origin == null
        || (ai != null && !id.equals(ai.resultId))
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
    this.origin = origin;
    this.ai = ai;
    concentration = calibration.concentration(samplePeak - baselinePeak);
  }

  public String status() {
    return origin.label + " · " + (calibration.inRange(concentration) ? "待判定（标曲范围内）" : "超出标曲范围");
  }

  public DetectionResult withAi(AiReport report) {
    if (report == null) throw new IllegalArgumentException("缺少 AI 返回结果");
    return new DetectionResult(
        id,
        sampleId,
        drug,
        epochMillis,
        baselinePeak,
        samplePeak,
        calibration,
        signal,
        origin,
        report);
  }
}
