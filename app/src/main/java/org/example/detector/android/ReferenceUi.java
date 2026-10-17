package org.example.detector.android;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** 根据设备参考图定义颜色、间距和原生控件，去除拍摄产生的屏幕偏色。 */
final class ReferenceUi {
  static final int BLUE = 0xff1764ed, INK = 0xff202c40, MUTED = 0xff8793a6, LINE = 0xffe5eaf2,
      PALE = 0xfff5f8fc, GREEN = 0xff28ad79;

  static int dp(Context c, int n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable shape(Context c, int color, int border, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    if (border != 0)
      d.setStroke(dp(c, 1), border);
    return d;
  }

  static LinearLayout column(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  static TextView label(Context c, String value, int size, int color) {
    TextView v = new TextView(c);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setGravity(Gravity.CENTER_VERTICAL);
    v.setIncludeFontPadding(false);
    v.setLineSpacing(dp(c, 4), 1);
    return v;
  }

  static Button action(Context c, String value, boolean primary, Runnable action) {
    Button b = new Button(c);
    b.setText(value);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setTextColor(primary ? Color.WHITE : BLUE);
    b.setTypeface(null, Typeface.BOLD);
    b.setMinHeight(0);
    b.setMinimumHeight(0);
    b.setPadding(dp(c, 8), 0, dp(c, 8), 0);
    android.content.res.ColorStateList colors = new android.content.res.ColorStateList(
        new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
        new int[]{primary ? 0xffc8d4e8 : PALE, primary ? BLUE : Color.WHITE});
    GradientDrawable normal = shape(c, primary ? BLUE : Color.WHITE, primary ? 0 : LINE, 6);
    normal.setColor(colors);
    b.setBackground(new android.graphics.drawable.RippleDrawable(
        android.content.res.ColorStateList.valueOf(0x221764ed), normal, null));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  static LinearLayout panel(Context c) {
    LinearLayout p = column(c);
    int d = dp(c, 13);
    p.setPadding(d, d, d, d);
    p.setBackground(shape(c, Color.WHITE, LINE, 7));
    return p;
  }

  static final class Icon extends View {
    private final String name;
    private final int color;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Icon(Context c, String name, int color) {
      super(c);
      this.name = name;
      this.color = color;
      setContentDescription(name);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      canvas.save();
      canvas.scale(getWidth() / 32f, getHeight() / 32f);
      p.setColor(color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.7f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      if (name.equals("records")) {
        canvas.drawRoundRect(7, 4, 25, 28, 2, 2, p);
        for (int y = 11; y <= 23; y += 6)
          canvas.drawLine(12, y, 21, y, p);
      } else if (name.equals("settings")) {
        canvas.drawCircle(16, 16, 9, p);
        canvas.drawCircle(16, 16, 3, p);
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4;
          canvas.drawLine(16 + (float) Math.cos(a) * 9, 16 + (float) Math.sin(a) * 9,
              16 + (float) Math.cos(a) * 13, 16 + (float) Math.sin(a) * 13, p);
        }
      } else if (name.equals("bluetooth")) {
        Path path = new Path();
        path.moveTo(16, 3);
        path.lineTo(23, 10);
        path.lineTo(10, 23);
        path.moveTo(10, 9);
        path.lineTo(23, 22);
        path.lineTo(16, 29);
        path.close();
        canvas.drawPath(path, p);
      } else if (name.equals("clock")) {
        canvas.drawCircle(16, 16, 12, p);
        canvas.drawLine(16, 8, 16, 16, p);
        canvas.drawLine(16, 16, 22, 19, p);
      } else if (name.equals("drop")) {
        Path path = new Path();
        path.moveTo(16, 3);
        path.cubicTo(12, 10, 7, 15, 7, 21);
        path.cubicTo(7, 32, 25, 32, 25, 21);
        path.cubicTo(25, 15, 20, 10, 16, 3);
        canvas.drawPath(path, p);
      } else if (name.equals("chart")) {
        canvas.drawLine(5, 4, 5, 27, p);
        canvas.drawLine(5, 27, 29, 27, p);
        Path path = new Path();
        path.moveTo(7, 24);
        path.lineTo(13, 20);
        path.lineTo(19, 15);
        path.lineTo(27, 7);
        canvas.drawPath(path, p);
      } else {
        canvas.drawRoundRect(8, 3, 24, 28, 2, 2, p);
        canvas.drawRect(11, 7, 21, 19, p);
        canvas.drawCircle(16, 24, 1, p);
      }
      canvas.restore();
    }
  }

  static final class LoadingRing extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    LoadingRing(Context context) {
      super(context);
      setContentDescription("采集中");
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - 3;
      int phase = (int) (android.os.SystemClock.uptimeMillis() / 80 % 12);
      for (int i = 0; i < 12; i++) {
        double angle = i * Math.PI / 6;
        paint.setColor(BLUE);
        paint.setAlpha(45 + Math.floorMod(i - phase, 12) * 18);
        canvas.drawCircle(cx + (float) Math.cos(angle) * r, cy + (float) Math.sin(angle) * r,
            dp(getContext(), 2), paint);
      }
    }
  }

  static final class Stepper extends View {
    private final int current;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Stepper(Context c, int current) {
      super(c);
      this.current = current;
      setContentDescription("当前检测步骤 " + (current + 1));
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float density = getResources().getDisplayMetrics().density;
      canvas.save();
      canvas.scale(density, density);
      float w = getWidth() / density, spacing = (w - 32) / 4;
      String[] labels = {"活化", "加样", "反应", "冲洗", "检测"};
      for (int i = 0; i < 5; i++) {
        float x = 16 + i * spacing;
        if (i < 4) {
          p.setColor(i < current ? GREEN : LINE);
          p.setStrokeWidth(1.5f);
          canvas.drawLine(x + 12, 18, x + spacing - 12, 18, p);
        }
        p.setColor(i < current ? GREEN : i == current ? BLUE : PALE);
        canvas.drawRoundRect(x - 9, 9, x + 9, 27, 3, 3, p);
        p.setColor(i <= current ? Color.WHITE : MUTED);
        p.setTextSize(10);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(i < current ? "✓" : String.valueOf(i + 1), x, 22, p);
        p.setColor(i == current ? BLUE : MUTED);
        p.setTextSize(10);
        canvas.drawText(labels[i], x, 47, p);
      }
      canvas.restore();
    }
  }
}
