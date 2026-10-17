package org.example.detector.core;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 可独立运行的 Java 接口示例，生成真实可检查的请求、响应和 Excel。 */
public final class AiDemo {
  public static void main(String[] args) throws Exception {
    SimulatedWorkstation device = new SimulatedWorkstation();
    device.connect();
    Signal baseline = device.baseline(),
        sample = device.sample("AI-DEMO-001", DrugType.METHAMPHETAMINE);
    DetectionResult result =
        new DetectionResult(
            UUID.randomUUID().toString(),
            "AI-DEMO-001",
            DrugType.METHAMPHETAMINE,
            System.currentTimeMillis(),
            baseline.peak(),
            sample.peak(),
            DemoCatalog.defaultCurve().fit(),
            sample,
            DataOrigin.SIMULATED);
    AiRequest request = new AiRequest(result);
    AiReport report = AiAnalysis.run(new SimulatedAiAnalyzer(), request);
    if (args.length == 0) {
      System.out.println(request.json());
      System.out.println(report.json());
    } else {
      Path dir = Files.createDirectories(Paths.get(args[0]));
      Files.write(dir.resolve("ai-request.json"), request.json().getBytes(StandardCharsets.UTF_8));
      Files.write(dir.resolve("ai-response.json"), report.json().getBytes(StandardCharsets.UTF_8));
      try (OutputStream out = Files.newOutputStream(dir.resolve("ai-detection.xlsx"))) {
        XlsxExporter.write(Collections.singletonList(result.withAi(report)), out);
      }
      System.out.println("AI mock request, response and XLSX: " + dir.toAbsolutePath());
    }
  }
}
