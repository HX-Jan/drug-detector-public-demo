package org.example.detector.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;

/** Dependency-free executable acceptance tests; assertions are always active. */
public final class CoreVerification {
  private static int checks;

  private static void check(boolean pass, String message) {
    checks++;
    if (!pass) throw new AssertionError(message);
  }

  private static void near(double actual, double expected) {
    check(Math.abs(actual - expected) < 1e-8, "expected " + expected + ", got " + actual);
  }

  private static void rejects(Runnable action) {
    checks++;
    try {
      action.run();
    } catch (IllegalArgumentException | IllegalStateException expected) {
      return;
    }
    throw new AssertionError("Expected rejection");
  }

  public static void main(String[] args) throws Exception {
    Calibration c =
        Calibration.fit(
            DrugType.METHAMPHETAMINE,
            new double[] {1, 10, 25, 50, 100},
            new double[] {1, 1.28, 2.28, 4.48, 8.68, 16.88});
    near(c.slope, 0.08);
    near(c.intercept, 0.2);
    near(c.r, 1);
    near(c.concentration(1.8), 20);
    check(c.inRange(1) && c.inRange(100) && !c.inRange(101), "calibration boundaries");
    rejects(
        () ->
            Calibration.fit(c.drug, new double[] {1, 2, 2, 4, 5}, new double[] {0, 1, 2, 3, 4, 5}));
    rejects(() -> Calibration.fit(c.drug, new double[] {1, 2, 3, 4}, new double[] {0, 1, 2, 3, 4}));
    rejects(
        () ->
            Calibration.fit(
                c.drug,
                new double[] {1, 2, 3, 4, 5},
                new double[] {0, 1, 2, 3, 4, 5})); // constant deltas
    rejects(
        () ->
            Calibration.fit(
                c.drug, new double[] {1, 2, 3, 4, Double.NaN}, new double[] {0, 1, 3, 6, 10, 15}));
    rejects(
        () ->
            Calibration.fit(
                c.drug,
                new double[] {1, 2, 3, 4, 5},
                new double[] {0, 5, 9, 12, 14, 15})); // negative slope
    rejects(
        () -> new Signal(new double[] {0, 1, 2}, new double[] {0, Double.POSITIVE_INFINITY, 1}));
    rejects(() -> new Signal(new double[] {0, 0, 2}, new double[] {0, 1, 2}));
    SimulatedWorkstation station = new SimulatedWorkstation();
    rejects(station::baseline);
    station.connect();
    AtomicLong now = new AtomicLong(0);
    DetectionSession flow = new DetectionSession("DEMO-001", c.drug, 5000, now::get);
    rejects(flow::confirmSample);
    flow.confirmElectrode();
    flow.confirmSample();
    rejects(flow::finishReaction);
    now.set(4999);
    check(flow.remainingMillis() == 1, "monotonic countdown");
    rejects(flow::finishReaction);
    now.set(5000);
    flow.finishReaction();
    DetectionSession skipped = new DetectionSession("DEMO-SKIP", c.drug, 300000, now::get);
    rejects(skipped::skipReaction);
    skipped.confirmElectrode();
    skipped.confirmSample();
    skipped.skipReaction();
    check(skipped.stage() == DetectionSession.Stage.READY, "skip reaction advances to ready");
    rejects(() -> flow.beginMeasurement(false));
    flow.beginMeasurement(true);
    rejects(
        () ->
            flow.complete(
                Calibration.demo(DrugType.MORPHINE),
                station.baseline(),
                station.sample("DEMO-001", c.drug),
                1000));
    DetectionResult result =
        flow.complete(
            c, station.baseline(), station.sample("DEMO-001", c.drug), 1000, station.origin());
    check(result.calibration.id.equals(c.id), "calibration snapshot");
    check(
        result.status().startsWith("模拟数据") && result.origin == DataOrigin.SIMULATED,
        "simulation label");
    near(c.concentration(result.samplePeak - result.baselinePeak), result.concentration);
    rejects(() -> flow.complete(c, station.baseline(), station.sample("DEMO-001", c.drug), 1000));
    flow.cancel();
    check(flow.stage() == DetectionSession.Stage.COMPLETED, "completion remains completed");
    rejects(() -> new DetectionSession("../person", c.drug, 1000, now::get));
    rejects(() -> new DetectionSession("=1+1", c.drug, 1000, now::get));
    DetectionSession cancelled = new DetectionSession("CANCEL", c.drug, 1000, now::get);
    cancelled.cancel();
    rejects(cancelled::confirmElectrode);
    byte[] encoded = ResultCodec.encode(result);
    DetectionResult decoded = ResultCodec.decode(encoded);
    near(decoded.concentration, result.concentration);
    check(decoded.signal.currents().length == 101, "raw trace persistence");
    check(decoded.id.equals(result.id), "record ID roundtrip");
    double[] mutable = decoded.signal.currents();
    mutable[50] = 999;
    check(decoded.signal.peak() != 999, "signal defensive copy");
    try {
      ResultCodec.decode(Arrays.copyOf(encoded, 20));
      throw new AssertionError("truncation accepted");
    } catch (IOException expected) {
      checks++;
    }
    Path directory = Files.createTempDirectory("detector-test-");
    try {
      ResultRepository repo = new FileResultRepository(directory);
      repo.save(result);
      check(new FileResultRepository(directory).all().size() == 1, "restart persistence");
      check(repo.search("001", c.drug).size() == 1, "combined search");
      check(repo.search("missing", null).isEmpty(), "empty search");
      check(repo.search("001", DrugType.MORPHINE).isEmpty(), "type filter");
      try {
        repo.save(result);
        throw new AssertionError("duplicate accepted");
      } catch (IOException expected) {
        checks++;
      }
    } finally {
      try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
        for (Path p : files) Files.delete(p);
      }
      Files.delete(directory);
    }
    ByteArrayOutputStream workbook = new ByteArrayOutputStream();
    XlsxExporter.write(Collections.singletonList(result), workbook);
    int entries = 0;
    boolean summary = false, traces = false;
    try (ZipInputStream zip =
        new ZipInputStream(new ByteArrayInputStream(workbook.toByteArray()))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        byte[] xml = zip.readAllBytes();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        entries++;
        String value = new String(xml, java.nio.charset.StandardCharsets.UTF_8);
        if (entry.getName().equals("xl/worksheets/sheet1.xml")) {
          summary =
              value.contains("SIMULATED") && value.contains("DEMO-001") && !value.contains("<f>");
        }
        if (entry.getName().equals("xl/worksheets/sheet2.xml")) {
          traces = value.contains("SIMULATED");
        }
      }
    }
    check(entries == 6 && summary && traces, "OOXML workbook parts, labels, raw samples");
    rejects(
        () -> {
          try {
            XlsxExporter.write(Collections.emptyList(), new ByteArrayOutputStream());
          } catch (IOException ex) {
            throw new UncheckedIOException(ex);
          }
        });
    station.disconnect();
    rejects(() -> station.sample("DEMO-001", c.drug));
    if (args.length > 0)
      try (OutputStream out = Files.newOutputStream(Paths.get(args[0]))) {
        XlsxExporter.write(Collections.singletonList(result), out);
      }
    System.out.println(
        "PASS: " + checks + " acceptance checks (math, workflow, storage, OOXML export)");
  }
}
