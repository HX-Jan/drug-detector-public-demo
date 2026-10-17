package org.example.detector.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/**
 * 生成标准 OOXML 工作簿；文字写入字符串单元格，避免样品编号被 Excel 当作公式执行。
 */
public final class XlsxExporter {
  private XlsxExporter() {
  }

  /** 通用双工作表导出，标曲参数与采集数据复用同一套安全单元格编码。 */
  static void tables(List<Object[]> summary, List<Object[]> samples, OutputStream out)
      throws IOException {
    try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
      entry(zip, "[Content_Types].xml",
          "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
      entry(zip, "_rels/.rels",
          "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
      entry(zip, "xl/workbook.xml",
          "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"标曲参数\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"采集数据\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
      entry(zip, "xl/_rels/workbook.xml.rels",
          "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/></Relationships>");
      int n = 0;
      for (List<Object[]> data : Arrays.asList(summary, samples)) {
        StringBuilder sheet = start();
        for (Object[] cells : data)
          row(sheet, cells);
        entry(zip, "xl/worksheets/sheet" + (++n) + ".xml",
            sheet.append("</sheetData></worksheet>").toString());
      }
    }
  }

  public static void write(List<DetectionResult> records, OutputStream destination)
      throws IOException {
    if (records.isEmpty())
      throw new IllegalArgumentException("请选择至少一条记录");
    try (ZipOutputStream zip = new ZipOutputStream(destination, StandardCharsets.UTF_8)) {
      entry(zip, "[Content_Types].xml",
          "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default"
              + " Extension=\"rels\""
              + " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default"
              + " Extension=\"xml\" ContentType=\"application/xml\"/><Override"
              + " PartName=\"/xl/workbook.xml\""
              + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override"
              + " PartName=\"/xl/worksheets/sheet1.xml\""
              + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override"
              + " PartName=\"/xl/worksheets/sheet2.xml\""
              + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
      entry(zip, "_rels/.rels", "<Relationships"
          + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship"
          + " Id=\"rId1\""
          + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\""
          + " Target=\"xl/workbook.xml\"/></Relationships>");
      entry(zip, "xl/workbook.xml",
          "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
              + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet"
              + " name=\"检测记录\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"信号采样\" sheetId=\"2\""
              + " r:id=\"rId2\"/></sheets></workbook>");
      entry(zip, "xl/_rels/workbook.xml.rels", "<Relationships"
          + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship"
          + " Id=\"rId1\""
          + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\""
          + " Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\""
          + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\""
          + " Target=\"worksheets/sheet2.xml\"/></Relationships>");
      StringBuilder summary = start(), traces = start();
      row(summary, "数据来源", "记录ID", "样品编号", "类型", "浓度 ng/L", "检测时间 UTC", "状态", "I0 μA", "I1 μA",
          "ΔI μA", "标曲ID", "k", "b", "r", "下限 ng/L", "上限 ng/L");
      row(traces, "数据来源", "记录ID", "电位 V", "电流 μA");
      for (DetectionResult r : records) {
        row(summary, "detector-result", r.id, r.sampleId, r.drug.label, r.concentration,
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(r.epochMillis)), r.status(),
            r.baselinePeak, r.samplePeak, r.samplePeak - r.baselinePeak, r.calibration.id,
            r.calibration.slope, r.calibration.intercept, r.calibration.r, r.calibration.min,
            r.calibration.max);
        double[] v = r.signal.voltages(), i = r.signal.currents();
        for (int n = 0; n < v.length; n++)
          row(traces, "detector-result", r.id, v[n], i[n]);
      }
      entry(zip, "xl/worksheets/sheet1.xml", summary.append("</sheetData></worksheet>").toString());
      entry(zip, "xl/worksheets/sheet2.xml", traces.append("</sheetData></worksheet>").toString());
    }
  }

  private static StringBuilder start() {
    return new StringBuilder("<worksheet"
        + " xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
  }

  private static void row(StringBuilder xml, Object... values) {
    xml.append("<row>");
    for (Object value : values) {
      if (value instanceof Number)
        xml.append("<c><v>").append(value).append("</v></c>");
      else
        xml.append("<c t=\"inlineStr\"><is><t xml:space=\"preserve\">")
            .append(escape(String.valueOf(value))).append("</t></is></c>");
    }
    xml.append("</row>");
  }

  private static String escape(String value) {
    return value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "").replace("&", "&amp;")
        .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  private static void entry(ZipOutputStream zip, String name, String text) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + text)
        .getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
