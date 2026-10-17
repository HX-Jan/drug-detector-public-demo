package org.example.detector.android;

import static org.example.detector.android.ReferenceUi.*;
import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.function.*;
import org.example.detector.core.*;

/** 标曲向导的 Android 原生呈现层；字段采用截图中的居中触屏键盘编辑。 */
final class AndroidCurveEditor implements CurveWorkflow.View {
  private final MainActivity app;
  private final CurveWorkflow workflow;
  AndroidCurveEditor(MainActivity app, StandardCurve curve) {
    this.app = app;
    workflow = new CurveWorkflow(this, app.library, curve, () -> {
    });
  }
  void start() {
    workflow.show(0);
  }
  @Override
  public void page(String title, String subtitle, String[] tabs, int selected, IntConsumer change) {
    app.page(title, subtitle, true);
    replaceBack(app.getWindow().getDecorView());
    LinearLayout row = new LinearLayout(app);
    for (int i = 0; i < tabs.length; i++) {
      int index = i;
      LinearLayout cell = column(app);
      TextView text = app.label(tabs[i], tabs.length == 5 ? 11 : 12, i == selected ? BLUE : MUTED);
      text.setGravity(Gravity.CENTER);
      cell.addView(text, new LinearLayout.LayoutParams(-1, app.d(42)));
      View underline = new View(app);
      underline.setBackgroundColor(i == selected ? BLUE : Color.WHITE);
      cell.addView(underline, new LinearLayout.LayoutParams(-1, app.d(2)));
      cell.setOnClickListener(v -> app.safe(() -> change.accept(index)));
      text.setOnClickListener(v -> cell.performClick());
      row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
    }
    app.add(app.body, row, 44, 18);
  }
  private void replaceBack(View view) {
    if ("返回首页"
        .contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()))
      view.setOnClickListener(v -> app.calibrationManager());
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
        replaceBack(((ViewGroup) view).getChildAt(i));
  }
  @Override
  public void text(String value, boolean muted) {
    LinearLayout p = app.card();
    app.text(p, value, 12, muted ? MUTED : INK);
  }
  @Override
  public void baseline(String value, Runnable rescan) {
    LinearLayout p = app.card(), row = new LinearLayout(app);
    row.addView(app.label(value, 10, INK), new LinearLayout.LayoutParams(0, app.d(36), 1));
    Button button = app.button("重新采集", false, rescan);
    button.setTextSize(10);
    row.addView(button, new LinearLayout.LayoutParams(app.d(74), app.d(36)));
    p.addView(row);
  }
  @Override
  public void field(String title, String value, boolean alphabet, Consumer<String> changed) {
    app.text(app.body, title, 11, MUTED);
    Button b = app.button(value.isEmpty() ? "点击输入" : value, false,
        () -> keyboard(title, value, alphabet, changed));
    b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    b.setTextColor(INK);
    b.setTypeface(null, Typeface.NORMAL);
    b.setTag(title);
    app.add(app.body, b, 42, 15);
  }
  @Override
  public void choice(String label, String[] options, int selected, IntConsumer changed) {
    app.text(app.body, label, 11, MUTED);
    Spinner spinner = new Spinner(app);
    spinner.setAdapter(
        new ArrayAdapter<>(app, android.R.layout.simple_spinner_dropdown_item, options));
    spinner.setSelection(selected);
    spinner.setBackground(shape(app, Color.WHITE, LINE, 5));
    app.add(app.body, spinner, 42, 16);
    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onNothingSelected(AdapterView<?> p) {
      }
      public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
        if (position != selected)
          app.safe(() -> changed.accept(position));
      }
    });
  }
  @Override
  public void range(String title, double start, double end, DoubleConsumer startChanged,
      DoubleConsumer endChanged) {
    app.text(app.body, title + " (mV)", 11, MUTED);
    LinearLayout row = new LinearLayout(app);
    double[] values = {start, end};
    DoubleConsumer[] actions = {startChanged, endChanged};
    for (int i = 0; i < 2; i++) {
      int n = i;
      String label = title + (i == 0 ? "起点" : "终点") + " (mV)";
      Button b = app.button(StandardCurve.number(values[i]), false,
          () -> keyboard(label, StandardCurve.number(values[n]), false, s -> {
            double v = StandardCurve.parseNumber(s);
            StandardCurve.finite(v);
            actions[n].accept(v);
          }));
      b.setTag(label);
      b.setTextColor(INK);
      b.setTypeface(null, Typeface.NORMAL);
      b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      row.addView(b, new LinearLayout.LayoutParams(0, app.d(42), 1));
      if (i == 0) {
        TextView to = app.label("至", 12, MUTED);
        to.setGravity(Gravity.CENTER);
        row.addView(to, new LinearLayout.LayoutParams(app.d(32), app.d(42)));
      }
    }
    app.add(app.body, row, 42, 18);
  }
  @Override
  public void buttons(String[] labels, boolean[] primary, Runnable[] actions, boolean footer) {
    LinearLayout row = new LinearLayout(app);
    for (int i = 0; i < labels.length; i++) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, app.d(46), 1);
      if (i > 0)
        lp.leftMargin = app.d(8);
      row.addView(app.button(labels[i], primary[i], actions[i]), lp);
    }
    app.add(footer ? app.footer : app.body, row, 46, footer ? 0 : 12);
  }
  @Override
  public void plot(Signal signal, String unit, String title) {
    LinearLayout p = app.card();
    app.text(p, title, 12, INK);
    app.add(p, new SignalChart(app, signal, unit), 245, 0);
  }
  @Override
  public void progress(String title, String message, int count, IntConsumer acquire, Runnable complete) {
    round(title, message, 1, count, acquire, complete);
  }
  private void round(String title, String message, int n, int count, IntConsumer acquire, Runnable complete) {
    app.progress(title, "第 " + n + "/" + count + " 次 · " + message, 450, () -> {
      acquire.accept(n - 1);
      if (n < count)
        round(title, message, n + 1, count, acquire, complete);
      else
        complete.run();
    });
  }
  /** 所有自定义弹窗均受 Activity 生命周期管理，关闭页面后不会继续触发旧回调。 */
  private void dialog(LinearLayout content) {
    app.closeOverlay();
    Dialog dialog = new Dialog(app);
    app.overlay = dialog;
    ScrollView scroll = new ScrollView(app);
    scroll.setFillViewport(false);
    scroll.addView(content);
    dialog.setContentView(scroll);
    dialog.show();
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
      dialog.getWindow().setLayout(
          Math.min(app.getResources().getDisplayMetrics().widthPixels - app.d(42), app.d(340)), -2);
    }
  }
  @Override
  public void confirm(String title, String message, Runnable confirmed) {
    LinearLayout p = panel(app);
    TextView heading = app.label(title, 17, INK);
    heading.setGravity(Gravity.CENTER);
    heading.setTypeface(null, Typeface.BOLD);
    app.add(p, heading, 40, 8);
    TextView detail = app.label(message, 13, INK);
    detail.setGravity(Gravity.CENTER);
    app.add(p, detail, -2, 20);
    LinearLayout row = new LinearLayout(app);
    row.addView(app.button("取消", false, app::closeOverlay),
        new LinearLayout.LayoutParams(0, app.d(42), 1));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, app.d(42), 1);
    lp.leftMargin = app.d(8);
    Button confirm = app.button(title.startsWith("删除") ? "确认删除" : "确认", true, () -> {
      app.closeOverlay();
      confirmed.run();
    });
    if (title.startsWith("删除"))
      confirm.setBackground(shape(app, 0xffd84d59, 0, 5));
    row.addView(confirm, lp);
    p.addView(row);
    dialog(p);
  }
  @Override
  public void message(String value) {
    app.toast(value);
  }
  @Override
  public void details(String title, String value) {
    LinearLayout p = panel(app);
    TextView heading = app.label(title, 16, INK);
    heading.setGravity(Gravity.CENTER);
    app.add(p, heading, 38, 12);
    ScrollView scroll = new ScrollView(app);
    TextView detail = app.label(value, 12, INK);
    scroll.addView(detail);
    app.add(p, scroll, 360, 12);
    app.add(p, app.button("关闭", true, app::closeOverlay), 42, 0);
    dialog(p);
  }
  private File localDirectory() {
    File parent = app.getExternalFilesDir(null);
    return new File(parent == null ? app.getFilesDir() : parent, "exports/standard_curves");
  }
  @Override
  public String destination() {
    return "本地：" + localDirectory().getAbsolutePath() + "\nU 盘：通过系统文件选择器选择已挂载目录";
  }
  @Override
  public void export(StandardCurve curve, boolean usb) {
    if (usb) {
      app.exportCurveToDocument(curve);
      return;
    }
    try {
      java.nio.file.Path output = CurveExport.write(curve, localDirectory().toPath());
      message("已导出 JSON 与 Excel：" + output);
    } catch (IOException e) {
      throw new IllegalStateException("导出失败：" + e.getMessage(), e);
    }
  }
  private void keyboard(String title, String initial, boolean alphabet,
      Consumer<String> confirmed) {
    LinearLayout p = panel(app);
    TextView heading = app.label("输入" + title, 16, INK);
    heading.setGravity(Gravity.CENTER);
    app.add(p, heading, 34, 8);
    // 由下方触屏键盘维护文本，避免系统软键盘遮挡参考图中的自定义按键。
    TextView input = app.label("",20,INK);
    String value = initial.replace("pg/mL", "").trim();
    input.setText(value.equals("未计算") ? "" : value);
    input.setTag("keypad-input");
    input.setMaxLines(1);
    input.setGravity(Gravity.CENTER);
    input.setTextSize(20);
    input.setTextColor(INK);
    input.setPadding(0, 0, 0, 0);
    input.setBackground(shape(app, PALE, LINE, 4));
    app.add(p, input, 44, 12);
    Runnable submit = () -> {
      confirmed.accept(input.getText().toString().trim());
      app.closeOverlay();
    };
    LinearLayout keys = column(app);
    p.addView(keys);
    if (alphabet) {
      alphabet(keys, input, 0);
      keyRows(p, new String[]{"删除", "清除", "取消", "确认"}, 4, input, submit);
    } else
      keyRows(keys, new String[]{"1", "2", "3", "删除", "4", "5", "6", "清除", "7", "8", "9", "取消", ".",
          "0", "-", "确认"}, 4, input, submit);
    dialog(p);
  }
  private void keyRows(LinearLayout parent, String[] entries, int columns, TextView input,
      Runnable submit) {
    for (int start = 0; start < entries.length; start += columns) {
      LinearLayout row = new LinearLayout(app);
      for (int i = start; i < Math.min(entries.length, start + columns); i++) {
        String key = entries[i];
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, app.d(44), 1);
        lp.setMargins(app.d(2), app.d(3), app.d(2), app.d(3));
        row.addView(app.button(key, key.equals("确认"), () -> {
          if (key.equals("确认")) {
            submit.run();
            return;
          }
          if (key.equals("取消")) {
            app.closeOverlay();
            return;
          }
          if (key.equals("清除")) {
            input.setText("");
            return;
          }
          if (key.equals("删除")) {
            if (input.length() > 0)
              input.setText(input.getText().subSequence(0, input.length() - 1));
            return;
          }
          input.append(key);
        }), lp);
      }
      parent.addView(row);
    }
  }
  private void alphabet(LinearLayout keys, TextView input, int page) {
    keys.removeAllViews();
    String[] entries = page == 0
        ? new String[]{"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N-Z",
            "0-9"}
        : page == 1
            ? new String[]{"N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "A-M",
                "0-9"}
            : new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "_", "-", "A-M", "N-Z",
                "."};
    for (int start = 0; start < entries.length; start += 5) {
      LinearLayout row = new LinearLayout(app);
      for (int i = start; i < start + 5; i++) {
        String key = entries[i];
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, app.d(46), 1);
        lp.setMargins(app.d(2), app.d(3), app.d(2), app.d(3));
        row.addView(app.button(key, false, () -> {
          if (key.equals("N-Z"))
            alphabet(keys, input, 1);
          else if (key.equals("A-M"))
            alphabet(keys, input, 0);
          else if (key.equals("0-9"))
            alphabet(keys, input, 2);
          else
            input.append(key);
        }), lp);
      }
      keys.addView(row);
    }
  }
}
