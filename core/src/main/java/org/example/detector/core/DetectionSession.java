package org.example.detector.core;

import java.util.function.LongSupplier;

/** 检测流程状态机。使用单调时钟计算反应时间，防止修改系统时间后提前检测或重复完成。 */
public final class DetectionSession {
  public enum Stage {
    // 顺序对应界面的活化、加样、反应、冲洗、检测五步；界面不得自行跳过状态。
    PREPARED, SAMPLE_ADDED, REACTING, READY, MEASURING, COMPLETED, CANCELLED
  }

  public final String sampleId;
  public final DrugType drug;
  private final LongSupplier clock;
  private final long reactionMillis;
  private Stage stage = Stage.PREPARED;
  private long start;

  public DetectionSession(String sampleId, DrugType drug, long reactionMillis, LongSupplier clock) {
    if (sampleId == null || !sampleId.matches("[A-Za-z0-9_-]{1,40}") || drug == null
        || reactionMillis <= 0)
      throw new IllegalArgumentException("样品编号限 1–40 位字母、数字、下划线或短横线");
    this.sampleId = sampleId;
    this.drug = drug;
    this.reactionMillis = reactionMillis;
    this.clock = clock;
  }

  public Stage stage() {
    return stage;
  }

  private void require(Stage expected) {
    if (stage != expected)
      throw new IllegalStateException("当前流程状态不允许此操作：" + stage);
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
    // 防御测试时钟回退；只有反应剩余时间归零后才能进入冲洗步骤。
    return Math.max(0, reactionMillis - Math.max(0, clock.getAsLong() - start));
  }

  public void finishReaction() {
    require(Stage.REACTING);
    if (remainingMillis() > 0)
      throw new IllegalStateException("反应计时尚未结束");
    stage = Stage.READY;
  }

  /** 展示版快速跳过反应等待；只允许从反应阶段进入冲洗阶段。 */
  public void skipReaction() {
    require(Stage.REACTING);
    stage = Stage.READY;
  }

  public void beginMeasurement(boolean connected) {
    require(Stage.READY);
    if (!connected)
      throw new IllegalStateException("设备未连接");
    stage = Stage.MEASURING;
  }

  public DetectionResult complete(Calibration calibration, Signal baseline, Signal sample,
      long epochMillis) {
    require(Stage.MEASURING);
    if (calibration.drug != drug)
      throw new IllegalArgumentException("标曲与检测类型不匹配");
    // 结果保存当次标曲快照，之后修改标曲不应改变历史结果的解释。
    DetectionResult result = new DetectionResult(java.util.UUID.randomUUID().toString(), sampleId,
        drug, epochMillis, baseline.peak(), sample.peak(), calibration, sample);
    stage = Stage.COMPLETED;
    return result;
  }

  public void cancel() {
    if (stage != Stage.COMPLETED)
      stage = Stage.CANCELLED;
  }
}
