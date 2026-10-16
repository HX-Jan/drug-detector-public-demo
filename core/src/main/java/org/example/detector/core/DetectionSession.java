package org.example.detector.core;

import java.util.function.LongSupplier;

/** Monotonic clock + explicit transitions prevent premature detection and repeated completion. */
public final class DetectionSession {
  public enum Stage {
    PREPARED,
    SAMPLE_ADDED,
    REACTING,
    READY,
    MEASURING,
    COMPLETED,
    CANCELLED
  }

  public final String sampleId;
  public final DrugType drug;
  private final LongSupplier clock;
  private final long reactionMillis;
  private Stage stage = Stage.PREPARED;
  private long start;

  public DetectionSession(String sampleId, DrugType drug, long reactionMillis, LongSupplier clock) {
    if (sampleId == null
        || !sampleId.matches("[A-Za-z0-9_-]{1,40}")
        || drug == null
        || reactionMillis <= 0) throw new IllegalArgumentException("样品编号限 1–40 位字母、数字、下划线或短横线");
    this.sampleId = sampleId;
    this.drug = drug;
    this.reactionMillis = reactionMillis;
    this.clock = clock;
  }

  public Stage stage() {
    return stage;
  }

  private void require(Stage expected) {
    if (stage != expected) throw new IllegalStateException("当前流程状态不允许此操作：" + stage);
  }

  public void confirmElectrode() {
    require(Stage.PREPARED);
    stage = Stage.SAMPLE_ADDED;
  }

  public void confirmSample() {
    require(Stage.SAMPLE_ADDED);
    start = clock.getAsLong();
    stage = Stage.REACTING;
  }

  public long remainingMillis() {
    return Math.max(0, reactionMillis - Math.max(0, clock.getAsLong() - start));
  }

  public void finishReaction() {
    require(Stage.REACTING);
    if (remainingMillis() > 0) throw new IllegalStateException("反应计时尚未结束");
    stage = Stage.READY;
  }

  public void beginMeasurement(boolean connected) {
    require(Stage.READY);
    if (!connected) throw new IllegalStateException("设备未连接");
    stage = Stage.MEASURING;
  }

  public DetectionResult complete(
      Calibration calibration, Signal baseline, Signal sample, long epochMillis) {
    require(Stage.MEASURING);
    if (calibration.drug != drug) throw new IllegalArgumentException("标曲与检测类型不匹配");
    DetectionResult result =
        new DetectionResult(
            java.util.UUID.randomUUID().toString(),
            sampleId,
            drug,
            epochMillis,
            baseline.peak(),
            sample.peak(),
            calibration,
            sample);
    stage = Stage.COMPLETED;
    return result;
  }

  public void cancel() {
    if (stage != Stage.COMPLETED) stage = Stage.CANCELLED;
  }
}
