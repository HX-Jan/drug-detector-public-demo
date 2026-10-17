package org.example.detector.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** 用独立已知曲线验证 AI 接口、替换、失败、持久化和导出；不验证真实模型性能。 */
public final class AiVerification {
  private static int checks;

  private static void check(boolean condition, String label) {
    checks++;
    if (!condition) throw new AssertionError(label);
  }

  private static void near(double a, double b) {
    check(Math.abs(a - b) < 1e-10, a + " != " + b);
  }

  private static DetectionResult result(Signal signal) {
    return new DetectionResult(
        UUID.randomUUID().toString(),
        "AI-TEST",
        DrugType.METHAMPHETAMINE,
        1,
        1,
        signal.peak(),
        new Calibration("known", DrugType.METHAMPHETAMINE, .5, 0, 1, 1, 10),
        signal,
        DataOrigin.SIMULATED);
  }

  private static void rejects(Runnable action) {
    try {
      action.run();
    } catch (IllegalArgumentException expected) {
      checks++;
      return;
    }
    throw new AssertionError("invalid AI response accepted");
  }

  public static void main(String[] args) throws Exception {
    DetectionResult raw =
        result(new Signal(new double[] {-.2, -.1, 0, .1, .2}, new double[] {1, 2, 4, 2, 1}));
    AiRequest request = new AiRequest(raw);
    check(
        request.resultId.equals(raw.id) && request.dataOrigin == DataOrigin.SIMULATED,
        "请求带记录关联与来源");
    check(
        request.json().contains("\"voltage\":\"V\"") && request.json().contains("\"selectedDrug\""),
        "请求显式量纲和人工所选类型");
    AiReport report = AiAnalysis.run(new SimulatedAiAnalyzer(), request);
    check(
        report.mode == AiReport.Mode.SIMULATED && report.status == AiReport.Status.COMPLETED,
        "模拟模式独立标记");
    check(report.features.sampleCount == 5 && !report.features.peakAtEdge, "内部峰采样特征");
    near(report.features.peakVoltageV, 0);
    near(report.features.peakCurrentUa, 4);
    near(report.features.netPeakUa, 3);
    near(report.features.edgeDriftUa, 0);
    check(report.predictedDrug == null && report.confidence == null, "模拟器不伪造分类或置信度");
    check(report.review == AiReport.Review.MANUAL_REVIEW, "建议人工复核");
    DetectionResult enriched = raw.withAi(report);
    near(enriched.concentration, raw.concentration);
    check(enriched.id.equals(raw.id) && enriched.signal == raw.signal, "AI 不替换原始信号或检测结果");
    AiReport flat =
        AiAnalysis.run(
            new SimulatedAiAnalyzer(),
            new AiRequest(result(new Signal(new double[] {-.2, 0, .2}, new double[] {1, 1, 1}))));
    check(flat.review == AiReport.Review.REACQUIRE, "无净峰建议复采");
    AiReport edge =
        AiAnalysis.run(
            new SimulatedAiAnalyzer(),
            new AiRequest(result(new Signal(new double[] {-.2, 0, .2}, new double[] {1, 2, 4}))));
    check(edge.review == AiReport.Review.REACQUIRE, "边缘峰建议复采");
    AiReport failed =
        AiAnalysis.run(
            r -> {
              throw new IOException("private server detail");
            },
            request);
    check(failed.status == AiReport.Status.FAILED && failed.features == null, "服务失败单独记录");
    check(
        !failed.message.contains("private") && failed.mode == AiReport.Mode.MODEL,
        "失败不冒充模拟成功或暴露内部错误");
    near(raw.withAi(failed).concentration, raw.concentration);
    AiReport mismatched =
        AiAnalysis.run(
            r ->
                new AiReport(
                    "wrong",
                    r.resultId,
                    "test",
                    "1",
                    AiReport.Mode.MODEL,
                    AiReport.Status.COMPLETED,
                    AiReport.Review.MANUAL_REVIEW,
                    1,
                    report.features,
                    null,
                    null,
                    "test",
                    "review"),
            request);
    check(mismatched.status == AiReport.Status.FAILED, "拒绝串单响应");
    AiReport injected =
        AiAnalysis.run(
            r ->
                new AiReport(
                    r.requestId,
                    r.resultId,
                    "test-adapter",
                    "1",
                    AiReport.Mode.MODEL,
                    AiReport.Status.COMPLETED,
                    AiReport.Review.MANUAL_REVIEW,
                    1,
                    report.features,
                    DrugType.MORPHINE,
                    .75,
                    "test fixture",
                    "review"),
            request);
    check(
        injected.status == AiReport.Status.COMPLETED && injected.predictedDrug == DrugType.MORPHINE,
        "接口可替换并返回不同于人工选择的模型类型（测试夹具）");
    rejects(
        () ->
            new AiReport(
                request.requestId,
                request.resultId,
                "x",
                "1",
                AiReport.Mode.MODEL,
                AiReport.Status.COMPLETED,
                AiReport.Review.MANUAL_REVIEW,
                1,
                report.features,
                DrugType.MORPHINE,
                1.01,
                "x",
                "x"));
    rejects(
        () ->
            raw.withAi(
                new AiReport(
                    request.requestId,
                    "other",
                    "x",
                    "1",
                    AiReport.Mode.MODEL,
                    AiReport.Status.COMPLETED,
                    AiReport.Review.MANUAL_REVIEW,
                    1,
                    report.features,
                    null,
                    null,
                    "x",
                    "x")));
    DetectionResult restored = ResultCodec.decode(ResultCodec.encode(enriched));
    check(
        restored.ai != null
            && restored.ai.modelId.equals("public-ai-mock")
            && restored.ai.requestId.equals(request.requestId),
        "AI 快照编码往返");
    near(restored.ai.features.netPeakUa, 3);
    check(
        ResultCodec.decode(ResultCodec.encode(raw.withAi(failed))).ai.status
            == AiReport.Status.FAILED,
        "失败状态可持久化");
    check(
        ResultCodec.decode(ResultCodec.encode(raw.withAi(injected))).ai.predictedDrug
            == DrugType.MORPHINE,
        "可选分类字段往返");
    Path data = Files.createTempDirectory("ai-repository-");
    new FileResultRepository(data).save(enriched);
    check(
        new FileResultRepository(data).all().get(0).ai.requestId.equals(request.requestId),
        "重启读回分析快照");
    for (int version : new int[] {0x44545031, 0x44545032}) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (DataOutputStream out = new DataOutputStream(buffer)) {
        out.writeInt(version);
        out.writeUTF(raw.id);
        out.writeUTF(raw.sampleId);
        out.writeUTF(raw.drug.name());
        out.writeLong(1);
        if (version == 0x44545032) out.writeUTF("SIMULATED");
        out.writeDouble(1);
        out.writeDouble(4);
        out.writeUTF("known");
        for (double d : new double[] {.5, 0, 1, 1, 10}) out.writeDouble(d);
        out.writeInt(3);
        for (int i = 0; i < 3; i++) {
          out.writeDouble(i);
          out.writeDouble(i);
        }
      }
      check(ResultCodec.decode(buffer.toByteArray()).ai == null, "旧 v1/v2 记录未分析，不回填假结果");
    }
    ByteArrayOutputStream workbook = new ByteArrayOutputStream();
    XlsxExporter.write(Arrays.asList(enriched, raw, raw.withAi(failed)), workbook);
    boolean found = false;
    try (ZipInputStream zip =
        new ZipInputStream(new ByteArrayInputStream(workbook.toByteArray()))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
        if (entry.getName().equals("xl/worksheets/sheet1.xml"))
          found =
              xml.contains("public-ai-mock")
                  && xml.contains("NOT_ANALYZED")
                  && xml.contains("FAILED")
                  && xml.contains("AI 分析方式");
      }
    }
    check(found, "Excel 分开保存模拟分析、旧记录和服务失败");
    check(
        report.json().contains("\"confidence\":null") && report.json().contains("SIMULATED"),
        "响应 JSON 明确模拟及空置信度");
    check(AiReport.quote("a\"\n\\").equals("\"a\\\"\\u000a\\\\\""), "JSON 控制字符转义");
    Path demo = data.resolve("runnable-demo");
    AiDemo.main(new String[] {demo.toString()});
    check(
        Files.isRegularFile(demo.resolve("ai-request.json"))
            && Files.isRegularFile(demo.resolve("ai-response.json"))
            && Files.isRegularFile(demo.resolve("ai-detection.xlsx")),
        "公开 Java 示例入口实际生成全部文件");
    System.out.println("PASS: " + checks + " AI interface checks");
  }
}
