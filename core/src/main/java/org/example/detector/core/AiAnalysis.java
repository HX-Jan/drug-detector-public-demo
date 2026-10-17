package org.example.detector.core;

import java.io.IOException;

/** 校验 AI 返回与请求关联；服务失败仍保留可保存的原始检测，不使用模拟器静默兜底。 */
public final class AiAnalysis {
  private AiAnalysis() {}

  public static AiReport run(AiAnalyzer analyzer, AiRequest request) {
    try {
      AiReport report = analyzer.analyze(request);
      if (report == null
          || !report.requestId.equals(request.requestId)
          || !report.resultId.equals(request.resultId))
        throw new IllegalArgumentException("AI 返回与当前请求不匹配");
      return report;
    } catch (IOException | RuntimeException ex) {
      return new AiReport(
          request.requestId,
          request.resultId,
          "unavailable",
          "unknown",
          analyzer instanceof SimulatedAiAnalyzer ? AiReport.Mode.SIMULATED : AiReport.Mode.MODEL,
          AiReport.Status.FAILED,
          AiReport.Review.NOT_EVALUATED,
          System.currentTimeMillis(),
          null,
          null,
          null,
          "AI 服务未返回有效结果，原始检测数据已保留。",
          "请检查分析服务后重新检测；本次结果仍需人工复核。");
    }
  }
}
