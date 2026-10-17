package org.example.detector.core;

import java.io.*;

/** 带版本标识和长度上限的结果编码，两端共用，防止损坏文件触发无界内存分配。 */
public final class ResultCodec {
  private ResultCodec() {}

  public static byte[] encode(DetectionResult r) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(buffer)) {
      out.writeInt(0x44545033);
      out.writeUTF(r.id);
      out.writeUTF(r.sampleId);
      out.writeUTF(r.drug.name());
      out.writeLong(r.epochMillis);
      out.writeUTF(r.origin.name());
      out.writeDouble(r.baselinePeak);
      out.writeDouble(r.samplePeak);
      Calibration c = r.calibration;
      out.writeUTF(c.id);
      out.writeDouble(c.slope);
      out.writeDouble(c.intercept);
      out.writeDouble(c.r);
      out.writeDouble(c.min);
      out.writeDouble(c.max);
      double[] v = r.signal.voltages(), i = r.signal.currents();
      out.writeInt(v.length);
      for (int n = 0; n < v.length; n++) {
        out.writeDouble(v[n]);
        out.writeDouble(i[n]);
      }
      AiCodec.write(out, r.ai);
    }
    return buffer.toByteArray();
  }

  public static DetectionResult decode(byte[] bytes) throws IOException {
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      int version = in.readInt();
      if (version != 0x44545031 && version != 0x44545032 && version != 0x44545033)
        throw new IOException("不支持的记录格式");
      String id = in.readUTF(), sample = in.readUTF();
      DrugType drug = DrugType.valueOf(in.readUTF());
      long time = in.readLong();
      DataOrigin origin =
          version != 0x44545031 ? DataOrigin.valueOf(in.readUTF()) : DataOrigin.UNKNOWN;
      double base = in.readDouble(), peak = in.readDouble();
      Calibration c =
          new Calibration(
              in.readUTF(),
              drug,
              in.readDouble(),
              in.readDouble(),
              in.readDouble(),
              in.readDouble(),
              in.readDouble());
      int count = in.readInt();
      if (count < 3 || count > 10000) throw new IOException("采样点数量无效");
      double[] v = new double[count], i = new double[count];
      for (int n = 0; n < count; n++) {
        v[n] = in.readDouble();
        i[n] = in.readDouble();
      }
      AiReport ai = version == 0x44545033 ? AiCodec.read(in) : null;
      if (in.available() != 0) throw new IOException("记录包含未识别的数据");
      return new DetectionResult(
          id, sample, drug, time, base, peak, c, new Signal(v, i), origin, ai);
    } catch (IllegalArgumentException ex) {
      throw new IOException("记录内容无效", ex);
    }
  }
}
