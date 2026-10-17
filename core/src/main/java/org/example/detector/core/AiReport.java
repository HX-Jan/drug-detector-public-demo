package org.example.detector.core;

/** 独立于检测计算的 AI 分析快照；推理方式与原始信号来源分别记录。 */
public final class AiReport {
  public enum Mode {
    SIMULATED,
    MODEL
  }

  public enum Status {
    COMPLETED,
    FAILED
  }

  public enum Review {
    MANUAL_REVIEW,
    REACQUIRE,
    NOT_EVALUATED
  }

  public final String requestId, resultId, modelId, modelVersion, message, recommendation;
  public final Mode mode;
  public final Status status;
  public final Review review;
  public final long epochMillis;
  public final AiFeatures features;
  public final DrugType predictedDrug;
  public final Double confidence;

  public AiReport(
      String requestId,
      String resultId,
      String modelId,
      String modelVersion,
      Mode mode,
      Status status,
      Review review,
      long epochMillis,
      AiFeatures features,
      DrugType predictedDrug,
      Double confidence,
      String message,
      String recommendation) {
    for (String value :
        new String[] {requestId, resultId, modelId, modelVersion, message, recommendation})
      if (value == null || value.isEmpty() || value.length() > 1500)
        throw new IllegalArgumentException("AI 返回文本无效");
    if (mode == null
        || status == null
        || review == null
        || epochMillis < 0
        || (status == Status.COMPLETED && features == null)
        || (confidence != null
            && (predictedDrug == null
                || !Double.isFinite(confidence)
                || confidence < 0
                || confidence > 1))) throw new IllegalArgumentException("AI 返回内容无效");
    this.requestId = requestId;
    this.resultId = resultId;
    this.modelId = modelId;
    this.modelVersion = modelVersion;
    this.mode = mode;
    this.status = status;
    this.review = review;
    this.epochMillis = epochMillis;
    this.features = features;
    this.predictedDrug = predictedDrug;
    this.confidence = confidence;
    this.message = message;
    this.recommendation = recommendation;
  }

  public String statusLabel() {
    return status == Status.FAILED ? "分析未完成" : mode == Mode.SIMULATED ? "模拟服务已返回" : "模型服务已返回";
  }

  public String reviewLabel() {
    return review == Review.REACQUIRE
        ? "建议重新采集"
        : review == Review.MANUAL_REVIEW ? "建议人工复核" : "尚未评估";
  }

  public String classification() {
    return predictedDrug == null ? "未执行分类，待真实模型接入" : predictedDrug.label;
  }

  public String modelDisplay() {
    return "服务    "
        + modelId
        + "\n版本    "
        + modelVersion
        + "\n方式    "
        + (mode == Mode.SIMULATED ? "模拟接口（规则演示）" : "模型推理")
        + "\n请求 ID    "
        + requestId;
  }

  public String json() {
    return "{\"schemaVersion\":1,\"requestId\":"
        + quote(requestId)
        + ",\"resultId\":"
        + quote(resultId)
        + ",\"mode\":"
        + quote(mode.name())
        + ",\"status\":"
        + quote(status.name())
        + ",\"modelId\":"
        + quote(modelId)
        + ",\"modelVersion\":"
        + quote(modelVersion)
        + ",\"epochMillis\":"
        + epochMillis
        + ",\"review\":"
        + quote(review.name())
        + ",\"predictedDrug\":"
        + (predictedDrug == null ? "null" : quote(predictedDrug.name()))
        + ",\"confidence\":"
        + (confidence == null ? "null" : confidence)
        + ",\"features\":"
        + (features == null ? "null" : features.json())
        + ",\"message\":"
        + quote(message)
        + ",\"recommendation\":"
        + quote(recommendation)
        + "}";
  }

  static String quote(String value) {
    StringBuilder result = new StringBuilder("\"");
    for (char c : value.toCharArray()) {
      if (c == '"' || c == '\\') result.append('\\').append(c);
      else if (c < 32) result.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
      else result.append(c);
    }
    return result.append('"').toString();
  }
}
