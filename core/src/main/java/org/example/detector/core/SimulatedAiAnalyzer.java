package org.example.detector.core;

/** 公开模拟适配器，无训练模型、私有代码或网络传输。只用明确的规则演示接口返回。 */
public final class SimulatedAiAnalyzer implements AiAnalyzer {
  @Override
  public AiReport analyze(AiRequest request) {
    AiFeatures features = AiFeatures.extract(request);
    boolean resample = features.peakAtEdge || features.netPeakUa <= 0;
    String suggestion = resample ? "请检查电极与扫描范围，重新采集后再进行人工复核。" : "请结合标准曲线、质控记录及实际实验进行人工复核。";
    if (!request.calibration.inRange(request.concentration)) suggestion += " 当前浓度超出标曲适用范围。";
    return new AiReport(
        request.requestId,
        request.resultId,
        "public-ai-mock",
        "1.0",
        AiReport.Mode.SIMULATED,
        AiReport.Status.COMPLETED,
        resample ? AiReport.Review.REACQUIRE : AiReport.Review.MANUAL_REVIEW,
        System.currentTimeMillis(),
        features,
        null,
        null,
        "公开规则模拟模型响应；已计算波形特征，尚未执行训练模型分类。",
        suggestion);
  }
}
