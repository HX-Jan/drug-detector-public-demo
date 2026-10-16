package org.example.detector.android;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.example.detector.core.Signal;

final class SignalChart extends View {
  private final Signal signal;
  private final String xUnit;
  private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);

  SignalChart(Context context, Signal signal) {
    this(context, signal, "V");
  }

  SignalChart(Context context, Signal signal, String xUnit) {
    super(context);
    this.signal = signal;
    this.xUnit = xUnit;
    setContentDescription("演示曲线，横轴 " + xUnit + "，纵轴微安");
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    double[] x = signal.voltages(), y = signal.currents();
    double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
    for (double value : y) {
      min = Math.min(min, value);
      max = Math.max(max, value);
    }
    double span = Math.max(0.1, max - min);
    float left = 70, top = 35, right = getWidth() - 25, bottom = getHeight() - 50;
    pen.setColor(Color.LTGRAY);
    pen.setStrokeWidth(1);
    for (int i = 0; i <= 4; i++) {
      float yy = top + (bottom - top) * i / 4;
      canvas.drawLine(left, yy, right, yy, pen);
    }
    pen.setColor(Color.DKGRAY);
    pen.setTextSize(24);
    canvas.drawText("μA", 8, 26, pen);
    canvas.drawText(xUnit, right - 50, bottom + 35, pen);
    canvas.drawText(String.format(java.util.Locale.ROOT, "%.2f", max), 3, top + 15, pen);
    canvas.drawText(String.format(java.util.Locale.ROOT, "%.2f", min), 3, bottom, pen);
    canvas.drawText(String.format(java.util.Locale.ROOT, "%.1f", x[0]), left, bottom + 35, pen);
    canvas.drawText(
        String.format(java.util.Locale.ROOT, "%.1f", x[x.length - 1]),
        right - 90,
        bottom + 35,
        pen);
    Path path = new Path();
    for (int i = 0; i < x.length; i++) {
      float px = left + (float) ((x[i] - x[0]) / (x[x.length - 1] - x[0])) * (right - left);
      float py = bottom - (float) ((y[i] - min) / span) * (bottom - top);
      if (i == 0) path.moveTo(px, py);
      else path.lineTo(px, py);
    }
    pen.setColor(Color.rgb(30, 104, 211));
    pen.setStyle(Paint.Style.STROKE);
    pen.setStrokeWidth(4);
    canvas.drawPath(path, pen);
    pen.setStyle(Paint.Style.FILL);
  }
}
