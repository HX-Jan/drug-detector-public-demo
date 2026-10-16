package org.example.detector.android;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.example.detector.core.*;

/** Native Java prototype. Hardware operations are deliberately provided by a simulator. */
public final class MainActivity extends Activity {
  private static final int EXPORT = 100;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Workstation workstation = new SimulatedWorkstation();
  private final EnumMap<DrugType, Calibration> curves = new EnumMap<>(DrugType.class);
  private SqliteResultRepository repository;
  private LinearLayout body;
  private DetectionSession session;
  private boolean accelerated = true;
  private int reactionMinutes = 5;
  private final List<DetectionResult> selected = new ArrayList<>();
  private List<DetectionResult> pendingExport;
  private DetectionResult unsaved;
  private final SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    repository = new SqliteResultRepository(this);
    accelerated = prefs().getBoolean("accelerated", true);
    reactionMinutes = prefs().getInt("minutes", 5);
    for (DrugType d : DrugType.values()) curves.put(d, loadCurve(d));
    home();
    if (state != null) toast("页面已重建，未完成的流程已取消；已保存记录可在历史记录中查看");
  }

  private SharedPreferences prefs() {
    return getSharedPreferences("demo-settings", MODE_PRIVATE);
  }

  private Calibration loadCurve(DrugType d) {
    try {
      String value = prefs().getString("curve-" + d.name(), null);
      if (value == null) return Calibration.demo(d);
      String[] f = value.split("\\|");
      return new Calibration(
          f[0],
          d,
          Double.parseDouble(f[1]),
          Double.parseDouble(f[2]),
          Double.parseDouble(f[3]),
          Double.parseDouble(f[4]),
          Double.parseDouble(f[5]));
    } catch (RuntimeException ex) {
      return Calibration.demo(d);
    }
  }

  private void saveCurve(Calibration c) {
    boolean saved =
        prefs()
            .edit()
            .putString(
                "curve-" + c.drug.name(),
                c.id + "|" + c.slope + "|" + c.intercept + "|" + c.r + "|" + c.min + "|" + c.max)
            .commit();
    if (!saved) throw new IllegalStateException("标曲保存失败");
    curves.put(c.drug, c);
  }

  private void page(String title) {
    LinearLayout outer = new LinearLayout(this);
    outer.setOrientation(LinearLayout.VERTICAL);
    outer.setBackgroundColor(0xfff3f6fb);
    // Respect system bars on Android 15 edge-to-edge devices.
    if (Build.VERSION.SDK_INT >= 30) {
      outer.setOnApplyWindowInsetsListener(
          (v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
          });
    } else outer.setFitsSystemWindows(true);
    TextView header = new TextView(this);
    header.setText("毒品直读检测仪  /  公开演示版");
    header.setTextSize(18);
    header.setTextColor(Color.WHITE);
    header.setBackgroundColor(0xff164a91);
    header.setPadding(dp(18), dp(20), dp(18), dp(20));
    outer.addView(header);
    TextView banner = new TextView(this);
    banner.setText("模拟工作站 · 合成数据 · 不作为真实检测结果");
    banner.setTextColor(0xff815600);
    banner.setTextSize(12);
    banner.setPadding(dp(18), dp(10), dp(18), dp(10));
    outer.addView(banner);
    ScrollView scroll = new ScrollView(this);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(18), dp(12), dp(18), dp(24));
    scroll.addView(body);
    outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(outer);
    outer.requestApplyInsets();
    text(title, 25);
    text(time.format(new Date()) + "  ·  " + (workstation.connected() ? "模拟设备已连接" : "模拟设备未连接"), 12);
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, int size) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(0xff20334d);
    v.setPadding(0, dp(8), 0, dp(8));
    body.addView(v);
    return v;
  }

  private Button button(String label, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    body.addView(b, new LinearLayout.LayoutParams(-1, dp(54)));
    b.setOnClickListener(v -> safe(action));
    return b;
  }

  private EditText input(String hint, String value) {
    EditText e = new EditText(this);
    e.setHint(hint);
    e.setText(value);
    e.setTextSize(16);
    body.addView(e);
    return e;
  }

  private Spinner drugPicker(boolean all) {
    Spinner s = new Spinner(this);
    List<Object> choices = new ArrayList<>();
    if (all) choices.add("全部类型");
    choices.addAll(Arrays.asList(DrugType.values()));
    ArrayAdapter<Object> a =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices);
    s.setAdapter(a);
    body.addView(s, new LinearLayout.LayoutParams(-1, dp(54)));
    return s;
  }

  private void safe(Runnable action) {
    try {
      action.run();
    } catch (Exception ex) {
      new AlertDialog.Builder(this)
          .setTitle("操作未完成")
          .setMessage(ex.getMessage())
          .setPositiveButton("知道了", null)
          .show();
    }
  }

  private void toast(String message) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  private void abandon() {
    handler.removeCallbacksAndMessages(null);
    if (session != null) session.cancel();
    session = null;
  }

  private void home() {
    abandon();
    page("检测工作台");
    text("独立实现的功能原型  v0.1.0", 14);
    button(
        workstation.connected() ? "断开模拟工作站" : "连接模拟工作站",
        () -> {
          if (workstation.connected()) workstation.disconnect();
          else workstation.connect();
          home();
        });
    button("样品测试", this::newSample);
    button("历史记录与导出", () -> history("", null));
    button("标准曲线", this::calibration);
    button("设备设置", this::settings);
    text("演示流程：登记样品 → 确认电极 → 加样 → 反应计时 → 冲洗与检测 → 保存结果", 16);
    text("真实 USB / BLE 协议、扫码及设备电池读取尚未接入。", 13);
  }

  private void newSample() {
    if (unsaved != null) {
      showUnsaved();
      return;
    }
    page("登记样品");
    text("仅填写虚构样品编号。结果单位统一为 ng/L。", 14);
    EditText id = input("样品编号", "DEMO-" + System.currentTimeMillis() % 1000000);
    Spinner drug = drugPicker(false);
    button(
        "进入检测引导",
        () -> {
          if (!workstation.connected()) throw new IllegalStateException("请返回首页连接模拟工作站");
          session =
              new DetectionSession(
                  id.getText().toString().trim(),
                  (DrugType) drug.getSelectedItem(),
                  accelerated ? 5000 : reactionMinutes * 60000L,
                  SystemClock::elapsedRealtime);
          step();
        });
    button("返回首页", this::home);
  }

  private void step() {
    page("样品检测 · " + session.drug.label);
    text("样品 " + session.sampleId, 16);
    switch (session.stage()) {
      case PREPARED:
        text("01  确认电极", 23);
        text("插入电极试条并保持稳定。公开版由人工确认，不调用真实扫码接口。", 16);
        button(
            "已确认电极",
            () -> {
              session.confirmElectrode();
              step();
            });
        break;
      case SAMPLE_ADDED:
        text("02  滴加样品", 23);
        text("按原型说明确认加样步骤（10 μL）。本页面仅演示操作顺序。", 16);
        button(
            "已完成加样，开始计时",
            () -> {
              session.confirmSample();
              step();
            });
        break;
      case REACTING:
        text("03  等待反应", 23);
        text(
            accelerated ? "演示加速：5 秒代表实际 5/10 分钟，不代表真实反应时间。" : "常规计时：" + reactionMinutes + " 分钟。",
            16);
        TextView remaining = text("", 36);
        Button next =
            button(
                "计时结束后继续",
                () -> {
                  session.finishReaction();
                  step();
                });
        next.setEnabled(false);
        handler.post(
            new Runnable() {
              public void run() {
                if (session == null || session.stage() != DetectionSession.Stage.REACTING) return;
                long left = session.remainingMillis();
                remaining.setText(
                    String.format(
                        Locale.ROOT, "%02d:%02d", left / 60000, (left % 60000 + 999) / 1000));
                next.setEnabled(left == 0);
                if (left > 0) handler.postDelayed(this, 250);
              }
            });
        break;
      case READY:
        text("04  冲洗与添加反应液", 23);
        text("确认冲洗、去除多余液体，并添加反应液（原型说明为 50 μL），然后启动模拟采集。", 16);
        button("开始模拟检测", this::measure);
        break;
      default:
        text("流程已结束，请返回工作台。", 16);
    }
    button("取消并返回首页", this::home);
  }

  private void measure() {
    session.beginMeasurement(workstation.connected());
    page("正在模拟采集");
    text("正在生成合成 DPV 信号，请稍等…", 18);
    ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);
    body.addView(progress);
    handler.post(
        new Runnable() {
          int value = 0;

          public void run() {
            if (session == null || session.stage() != DetectionSession.Stage.MEASURING) return;
            progress.setProgress(value += 10);
            if (value < 100) handler.postDelayed(this, 120);
            else
              safe(
                  () -> {
                    DetectionResult r =
                        session.complete(
                            curves.get(session.drug),
                            workstation.baseline(),
                            workstation.sample(session.sampleId, session.drug),
                            System.currentTimeMillis());
                    unsaved = r;
                    showUnsaved();
                  });
          }
        });
  }

  private void showUnsaved() {
    try {
      repository.save(unsaved);
      DetectionResult saved = unsaved;
      unsaved = null;
      detail(saved);
    } catch (IOException ex) {
      page("结果尚未保存");
      text(ex.getMessage() + "。请重试保存；当前结果仅在内存中。", 16);
      button("重试保存", this::showUnsaved);
    }
  }

  private void detail(DetectionResult r) {
    page("检测结果详情");
    text(String.format(Locale.ROOT, "%.3f ng/L", r.concentration), 34);
    text(r.status(), 16);
    text(
        "样品："
            + r.sampleId
            + "\n类型："
            + r.drug.label
            + "\n时间："
            + time.format(new Date(r.epochMillis)),
        16);
    text("合成 DPV 信号", 18);
    body.addView(new SignalChart(this, r.signal), new LinearLayout.LayoutParams(-1, dp(220)));
    text(
        String.format(
            Locale.ROOT,
            "I0 = %.4f μA  ·  I1 = %.4f μA\nΔI = %.4f μA",
            r.baselinePeak,
            r.samplePeak,
            r.samplePeak - r.baselinePeak),
        14);
    text(
        "当次标曲："
            + r.calibration.id
            + "\n"
            + r.calibration.equation()
            + "\n适用区间 "
            + r.calibration.min
            + "–"
            + r.calibration.max
            + " ng/L",
        13);
    button("导出本条 Excel", () -> export(Collections.singletonList(r)));
    button("查看历史记录", () -> history("", null));
    button("返回首页", this::home);
  }

  private void history(String query, DrugType filter) {
    page("历史记录");
    selected.clear();
    EditText search = input("按样品编号查询", query);
    Spinner drug = drugPicker(true);
    if (filter != null) drug.setSelection(filter.ordinal() + 1);
    button(
        "查询",
        () ->
            history(
                search.getText().toString().trim(),
                drug.getSelectedItem() instanceof DrugType
                    ? (DrugType) drug.getSelectedItem()
                    : null));
    button("导出勾选记录 Excel", () -> export(new ArrayList<>(selected)));
    button("返回首页", this::home);
    try {
      List<DetectionResult> records = repository.search(query, filter);
      text("共 " + records.size() + " 条模拟记录", 14);
      for (DetectionResult r : records) {
        CheckBox check = new CheckBox(this);
        check.setText(
            r.sampleId
                + " · "
                + r.drug.label
                + "\n"
                + String.format(Locale.ROOT, "%.3f ng/L", r.concentration)
                + " · "
                + time.format(new Date(r.epochMillis)));
        body.addView(check);
        check.setOnCheckedChangeListener(
            (v, checked) -> {
              if (checked) selected.add(r);
              else selected.remove(r);
            });
        button("查看详情 · " + r.sampleId, () -> detail(r));
      }
    } catch (IOException ex) {
      text("读取失败：" + ex.getMessage(), 16);
    }
  }

  private void export(List<DetectionResult> rows) {
    if (rows.isEmpty()) throw new IllegalArgumentException("请先勾选至少一条记录");
    pendingExport = new ArrayList<>(rows);
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.putExtra(
        Intent.EXTRA_TITLE, "SIMULATED-results-" + System.currentTimeMillis() + ".xlsx");
    startActivityForResult(intent, EXPORT);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == EXPORT) {
      if (result == RESULT_OK && data != null && data.getData() != null && pendingExport != null)
        safe(
            () -> {
              try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new IOException("无法打开目标文件");
                XlsxExporter.write(pendingExport, out);
                toast("Excel 已导出，所有结果均标记为模拟数据");
              } catch (IOException ex) {
                throw new IllegalStateException("导出失败：" + ex.getMessage(), ex);
              }
            });
      pendingExport = null;
    }
  }

  private void calibration() {
    page("标准曲线");
    text("默认曲线和下列标定点均为演示参数。只验证数学流程，不代表真实性能。", 14);
    Spinner drug = drugPicker(false);
    TextView current = text(curves.get(DrugType.values()[0]).equation(), 15);
    android.widget.FrameLayout plot = new android.widget.FrameLayout(this);
    body.addView(plot, new LinearLayout.LayoutParams(-1, dp(200)));
    Runnable updatePlot =
        () -> {
          Calibration curve = curves.get((DrugType) drug.getSelectedItem());
          plot.removeAllViews();
          plot.addView(new SignalChart(this, curve.fittedLine(), "ng/L"));
        };
    drug.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(android.widget.AdapterView<?> p) {}

          public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
            current.setText(curves.get((DrugType) drug.getSelectedItem()).equation());
            updatePlot.run();
          }
        });
    text("输入 5–10 个递增浓度（ng/L），再输入 I0 到 In（μA），用英文逗号分隔。", 14);
    EditText x = input("浓度", "1,10,25,50,100"),
        peaks = input("I0,I1,...,In", "1,1.28,2.28,4.48,8.68,16.88");
    button(
        "计算并保存演示标曲",
        () -> {
          Calibration c =
              Calibration.fit((DrugType) drug.getSelectedItem(), numbers(x), numbers(peaks));
          saveCurve(c);
          current.setText(c.equation() + "\n" + c.id);
          updatePlot.run();
          toast("已保存演示标曲；历史记录保留原有标曲");
        });
    button(
        "恢复该类型默认演示标曲",
        () -> {
          saveCurve(Calibration.demo((DrugType) drug.getSelectedItem()));
          current.setText(curves.get((DrugType) drug.getSelectedItem()).equation());
          updatePlot.run();
        });
    button("返回首页", this::home);
  }

  private double[] numbers(EditText input) {
    String[] fields = input.getText().toString().split(",", -1);
    double[] values = new double[fields.length];
    for (int i = 0; i < values.length; i++) values[i] = Double.parseDouble(fields[i].trim());
    return values;
  }

  private void settings() {
    page("设备设置");
    Switch speed = new Switch(this);
    speed.setText("演示加速计时（5 秒）");
    speed.setChecked(accelerated);
    body.addView(speed);
    speed.setOnCheckedChangeListener(
        (v, value) -> {
          accelerated = value;
          prefs().edit().putBoolean("accelerated", value).apply();
        });
    button(
        "常规反应时间：" + reactionMinutes + " 分钟（点击切换）",
        () -> {
          reactionMinutes = reactionMinutes == 5 ? 10 : 5;
          prefs().edit().putInt("minutes", reactionMinutes).apply();
          settings();
        });
    Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    text(scale > 0 ? "运行终端电量：" + (100 * level / scale) + "%" : "运行终端电量：不可用", 16);
    text("时间跟随 Android 系统，显示精确到秒。修改系统时间请使用系统设置。\n这里的电量属于运行终端；模拟工作站不提供真实硬件电量。", 14);
    button("返回首页", this::home);
  }

  @Override
  protected void onDestroy() {
    abandon();
    workstation.disconnect();
    repository.close();
    super.onDestroy();
  }
}
