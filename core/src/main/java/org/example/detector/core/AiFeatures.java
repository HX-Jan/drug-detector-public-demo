package org.example.detector.core;

/** 可复核的曲线数学特征；这些计算不代表已训练的 AI 模型。 */
public final class AiFeatures {
  public final int sampleCount;
  public final double peakVoltageV, peakCurrentUa, netPeakUa, edgeDriftUa;
  public final boolean peakAtEdge;

  public AiFeatures(
      int sampleCount,
      double peakVoltageV,
      double peakCurrentUa,
      double netPeakUa,
      double edgeDriftUa,
      boolean peakAtEdge) {
    StandardCurve.finite(peakVoltageV, peakCurrentUa, netPeakUa, edgeDriftUa);
    if (sampleCount < 3 || sampleCount > 10000) throw new IllegalArgumentException("AI 输入采样点数无效");
    this.sampleCount = sampleCount;
    this.peakVoltageV = peakVoltageV;
    this.peakCurrentUa = peakCurrentUa;
    this.netPeakUa = netPeakUa;
    this.edgeDriftUa = edgeDriftUa;
    this.peakAtEdge = peakAtEdge;
  }

  public static AiFeatures extract(AiRequest request) {
    double[] x = request.signal.voltages(), y = request.signal.currents();
    int peak = 0, edge = Math.max(1, y.length / 10);
    double first = 0, last = 0;
    for (int i = 0; i < y.length; i++) if (y[i] > y[peak]) peak = i;
    for (int i = 0; i < edge; i++) {
      first += y[i] / edge;
      last += y[y.length - 1 - i] / edge;
    }
    return new AiFeatures(
        y.length,
        x[peak],
        y[peak],
        y[peak] - request.baselinePeak,
        last - first,
        peak < edge || peak >= y.length - edge);
  }

  public String json() {
    return "{\"sampleCount\":"
        + sampleCount
        + ",\"peakVoltageV\":"
        + peakVoltageV
        + ",\"peakCurrentUa\":"
        + peakCurrentUa
        + ",\"netPeakUa\":"
        + netPeakUa
        + ",\"edgeDriftUa\":"
        + edgeDriftUa
        + ",\"peakAtEdge\":"
        + peakAtEdge
        + "}";
  }

  public String display() {
    return String.format(
        java.util.Locale.ROOT,
        "采样点数    %d\n峰电位    %.4f V\n峰电流    %.5f μA\n净峰电流    %.5f μA\n首尾均值差    %.5f μA\n峰位检查    %s",
        sampleCount,
        peakVoltageV,
        peakCurrentUa,
        netPeakUa,
        edgeDriftUa,
        peakAtEdge ? "靠近扫描边缘" : "位于扫描内部");
  }
}
