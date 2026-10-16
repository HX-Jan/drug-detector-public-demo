package org.example.detector.android;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;
import org.example.detector.core.Signal;

/** Density-independent axes for the narrow device viewport. */
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
    float density = getResources().getDisplayMetrics().density;
    canvas.save();
    canvas.scale(density, density);
    double[] x = signal.voltages(), y = signal.currents();
    double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
    for (double value : y) {
      min = Math.min(min, value);
      max = Math.max(max, value);
    }
    double span = Math.max(0.1, max - min);
    float left = 40,
        top = 22,
        right = getWidth() / density - 12,
        bottom = getHeight() / density - 35;
    pen.setStyle(Paint.Style.FILL);
    pen.setTextSize(9);
    pen.setTextAlign(Paint.Align.LEFT);
    pen.setColor(ReferenceUi.MUTED);
    canvas.drawText("μA", 4, 12, pen);
    for (int i = 0; i <= 4; i++) {
      float yy = top + (bottom - top) * i / 4;
      pen.setColor(ReferenceUi.LINE);
      pen.setStrokeWidth(0.7f);
      canvas.drawLine(left, yy, right, yy, pen);
      pen.setColor(ReferenceUi.MUTED);
      canvas.drawText(String.format(Locale.ROOT, "%.1f", max - span * i / 4), 2, yy + 3, pen);
    }
    canvas.drawText(String.format(Locale.ROOT, "%.1f", x[0]), left, bottom + 16, pen);
    pen.setTextAlign(Paint.Align.RIGHT);
    canvas.drawText(String.format(Locale.ROOT, "%.1f", x[x.length - 1]), right, bottom + 16, pen);
    pen.setTextAlign(Paint.Align.CENTER);
    canvas.drawText(xUnit, (left + right) / 2, bottom + 30, pen);
    Path path = new Path();
    for (int i = 0; i < x.length; i++) {
      float px = left + (float) ((x[i] - x[0]) / (x[x.length - 1] - x[0])) * (right - left),
          py = bottom - (float) ((y[i] - min) / span) * (bottom - top);
      if (i == 0) path.moveTo(px, py);
      else path.lineTo(px, py);
    }
    pen.setColor(ReferenceUi.BLUE);
    pen.setStyle(Paint.Style.STROKE);
    pen.setStrokeWidth(1.8f);
    canvas.drawPath(path, pen);
    pen.setStyle(Paint.Style.FILL);
    canvas.restore();
  }
}
