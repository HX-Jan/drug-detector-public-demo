package org.example.detector.core;

import java.io.*;

/** AI 快照附加在 v3 检测记录末尾，沿用结果编码的字段校验。 */
final class AiCodec {
  private AiCodec() {}

  static void write(DataOutputStream out, AiReport report) throws IOException {
    out.writeBoolean(report != null);
    if (report == null) return;
    out.writeUTF(report.requestId);
    out.writeUTF(report.resultId);
    out.writeUTF(report.modelId);
    out.writeUTF(report.modelVersion);
    out.writeUTF(report.mode.name());
    out.writeUTF(report.status.name());
    out.writeUTF(report.review.name());
    out.writeLong(report.epochMillis);
    out.writeUTF(report.message);
    out.writeUTF(report.recommendation);
    out.writeUTF(report.predictedDrug == null ? "" : report.predictedDrug.name());
    out.writeBoolean(report.confidence != null);
    if (report.confidence != null) out.writeDouble(report.confidence);
    out.writeBoolean(report.features != null);
    if (report.features != null) {
      AiFeatures f = report.features;
      out.writeInt(f.sampleCount);
      out.writeDouble(f.peakVoltageV);
      out.writeDouble(f.peakCurrentUa);
      out.writeDouble(f.netPeakUa);
      out.writeDouble(f.edgeDriftUa);
      out.writeBoolean(f.peakAtEdge);
    }
  }

  static AiReport read(DataInputStream in) throws IOException {
    if (!in.readBoolean()) return null;
    String request = in.readUTF(),
        result = in.readUTF(),
        model = in.readUTF(),
        version = in.readUTF();
    AiReport.Mode mode = AiReport.Mode.valueOf(in.readUTF());
    AiReport.Status status = AiReport.Status.valueOf(in.readUTF());
    AiReport.Review review = AiReport.Review.valueOf(in.readUTF());
    long time = in.readLong();
    String message = in.readUTF(), recommendation = in.readUTF(), predicted = in.readUTF();
    Double confidence = in.readBoolean() ? in.readDouble() : null;
    AiFeatures features =
        in.readBoolean()
            ? new AiFeatures(
                in.readInt(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readBoolean())
            : null;
    return new AiReport(
        request,
        result,
        model,
        version,
        mode,
        status,
        review,
        time,
        features,
        predicted.isEmpty() ? null : DrugType.valueOf(predicted),
        confidence,
        message,
        recommendation);
  }
}
