package org.example.detector.android;

import static org.example.detector.android.ReferenceUi.*;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.example.detector.core.*;

/** Reference-led native touch UI; acquisition remains explicitly simulated. */
public final class MainActivity extends Activity {
  private static final int EXPORT = 100;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Workstation workstation = new SimulatedWorkstation();
  private final EnumMap<DrugType, Calibration> curves = new EnumMap<>(DrugType.class);
  private SqliteResultRepository repository;
  private LinearLayout body, footer;
  private DetectionSession session;
  private boolean accelerated = true;
  private int reactionMinutes = 5;
  private List<DetectionResult> pendingExport;
  private DetectionResult unsaved;
  private Dialog overlay;
  private final SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    repository = new SqliteResultRepository(this);
    accelerated = prefs().getBoolean("accelerated", true);
    reactionMinutes = prefs().getInt("minutes", 5);
    for (DrugType d : DrugType.values()) curves.put(d, loadCurve(d));
    home();
    if (state != null) toast("未完成的流程已取消，已保存记录可在历史记录中查看");
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
    if (!prefs()
        .edit()
        .putString(
            "curve-" + c.drug.name(),
            c.id + "|" + c.slope + "|" + c.intercept + "|" + c.r + "|" + c.min + "|" + c.max)
        .commit()) throw new IllegalStateException("标曲保存失败");
    curves.put(c.drug, c);
  }

  private int d(int n) {
    return dp(this, n);
  }

  private TextView label(String value, int size, int color) {
    return ReferenceUi.label(this, value, size, color);
  }

  private void add(LinearLayout parent, View child, int height, int bottom) {
    LinearLayout.LayoutParams lp =
        new LinearLayout.LayoutParams(-1, height < 0 ? height : d(height));
    lp.bottomMargin = d(bottom);
    parent.addView(child, lp);
  }

  private void text(LinearLayout parent, String value, int size, int color) {
    add(parent, label(value, size, color), -2, 10);
  }

  private Button button(String value, boolean primary, Runnable action) {
    return ReferenceUi.action(this, value, primary, () -> safe(action));
  }

  private void action(String value, Runnable action) {
    add(footer, button(value, true, action), 46, 0);
  }

  private LinearLayout card() {
    LinearLayout p = panel(this);
    add(body, p, -2, 12);
    return p;
  }

  private void page(String title, String subtitle, boolean back) {
    closeOverlay();
    LinearLayout root = column(this);
    root.setBackgroundColor(Color.WHITE);
    if (Build.VERSION.SDK_INT >= 30)
      root.setOnApplyWindowInsetsListener(
          (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
          });
    else root.setFitsSystemWindows(true);
    TextView status = label(time.format(new Date()), 10, MUTED);
    status.setPadding(d(14), d(5), d(14), d(3));
    root.addView(status);
    LinearLayout heading = new LinearLayout(this);
    heading.setPadding(d(14), d(10), d(14), d(8));
    heading.setGravity(Gravity.CENTER_VERTICAL);
    if (back) {
      TextView arrow = label("‹", 30, INK);
      heading.addView(arrow, new LinearLayout.LayoutParams(d(26), d(40)));
      arrow.setContentDescription("返回首页");
      arrow.setOnClickListener(v -> home());
    }
    LinearLayout titles = column(this);
    TextView name = label(title, 18, INK);
    name.setTypeface(null, Typeface.BOLD);
    titles.addView(name);
    TextView sub = label(subtitle, 10, MUTED);
    sub.setPadding(0, d(5), 0, 0);
    titles.addView(sub);
    heading.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(heading);
    ScrollView scroll = new ScrollView(this);
    body = column(this);
    body.setPadding(d(14), d(8), d(14), d(8));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    footer = column(this);
    footer.setPadding(d(14), d(9), d(14), d(12));
    root.addView(footer);
    setContentView(root);
    root.requestApplyInsets();
  }

  private void note(String value) {
    TextView n = label(value, 11, MUTED);
    n.setGravity(Gravity.CENTER);
    add(footer, n, -2, 8);
  }

  private EditText field(LinearLayout parent, String hint, String value) {
    EditText f = new EditText(this);
    f.setSingleLine(true);
    f.setTextSize(13);
    f.setTextColor(INK);
    f.setHintTextColor(MUTED);
    f.setHint(hint);
    f.setText(value);
    f.setPadding(d(10), 0, d(10), 0);
    f.setBackground(shape(this, PALE, LINE, 5));
    add(parent, f, 42, 10);
    return f;
  }

  private Spinner picker(LinearLayout parent, boolean all) {
    Spinner s = new Spinner(this);
    List<Object> options = new ArrayList<>();
    if (all) options.add("全部类型");
    options.addAll(Arrays.asList(DrugType.values()));
    ArrayAdapter<Object> a =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options);
    s.setAdapter(a);
    s.setBackground(shape(this, PALE, LINE, 5));
    add(parent, s, 42, 10);
    return s;
  }

  private void info(String value) {
    LinearLayout p = card();
    p.setBackground(shape(this, PALE, LINE, 6));
    text(p, value, 12, BLUE);
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

  private void toast(String value) {
    Toast.makeText(this, value, Toast.LENGTH_LONG).show();
  }

  private void closeOverlay() {
    if (overlay != null) {
      overlay.dismiss();
      overlay = null;
    }
  }

  private void abandon() {
    handler.removeCallbacksAndMessages(null);
    closeOverlay();
    if (session != null) session.cancel();
    session = null;
  }

  private void home() {
    abandon();
    page("毒品快速检测仪", "电化学检测终端  ·  公开演示", false);
    LinearLayout connection = card();
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView dot = label("●", 15, workstation.connected() ? GREEN : MUTED);
    row.addView(dot, new LinearLayout.LayoutParams(d(22), -2));
    LinearLayout summary = column(this);
    summary.addView(label(workstation.connected() ? "设备已连接，就绪" : "设备未连接", 13, INK));
    summary.addView(label("模拟工作站  DEMO-P20", 10, MUTED));
    row.addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(
        label(
            workstation.connected() ? "已连接  ›" : "未连接  ›",
            11,
            workstation.connected() ? GREEN : MUTED));
    connection.addView(row);
    connection.setOnClickListener(v -> devicePage());
    Button start =
        button(
            workstation.connected() ? "开始样品测试" : "连接设备",
            true,
            workstation.connected() ? this::newSample : this::devicePage);
    add(body, start, 52, 4);
    TextView hint =
        label(workstation.connected() ? "活化 · 加样 · 反应 · 扫描 · 分析" : "连接后可开始样品测试", 10, MUTED);
    hint.setGravity(Gravity.CENTER);
    add(body, hint, 23, 15);
    LinearLayout shortcuts = new LinearLayout(this);
    String[] names = {"设备连接", "历史记录", "系统设置"}, icons = {"bluetooth", "records", "settings"};
    Runnable[] actions = {this::devicePage, () -> history("", null), this::settings};
    for (int i = 0; i < 3; i++) {
      final Runnable run = actions[i];
      LinearLayout tile = column(this);
      tile.setGravity(Gravity.CENTER);
      tile.addView(new Icon(this, icons[i], BLUE), new LinearLayout.LayoutParams(d(23), d(23)));
      TextView t = label(names[i], 11, INK);
      t.setGravity(Gravity.CENTER);
      t.setPadding(0, d(9), 0, 0);
      tile.addView(t);
      tile.setPadding(0, d(15), 0, d(15));
      tile.setBackground(shape(this, Color.WHITE, LINE, 6));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, d(87), 1);
      if (i > 0) lp.leftMargin = d(8);
      shortcuts.addView(tile, lp);
      tile.setOnClickListener(v -> safe(run));
    }
    add(body, shortcuts, -2, 12);
    if (!workstation.connected()) info("样品测试需先连接设备");
    int count = 0;
    try {
      String today = time.format(new Date()).substring(0, 10);
      for (DetectionResult r : repository.all())
        if (time.format(new Date(r.epochMillis)).startsWith(today)) count++;
    } catch (IOException ignored) {
    }
    note("演示模式 · 所有检测信号均为合成数据");
    LinearLayout stats = new LinearLayout(this);
    stats.addView(
        label("今日已检测 " + count + " 例", 10, MUTED), new LinearLayout.LayoutParams(0, -2, 1));
    TextView today =
        label(new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date()), 10, MUTED);
    today.setGravity(Gravity.RIGHT);
    stats.addView(today);
    add(footer, stats, 18, 0);
  }

  private void devicePage() {
    page("设备连接", "模拟工作站连接", true);
    LinearLayout p = card();
    text(p, "设备连接状态", 14, INK);
    text(
        p,
        workstation.connected() ? "● 已连接  DEMO-P20" : "○ 未连接设备",
        13,
        workstation.connected() ? GREEN : MUTED);
    text(p, "此设备为演示连接，不扫描真实蓝牙或 USB。", 11, MUTED);
    note("模拟设备  DEMO-P20");
    if (workstation.connected())
      action(
          "断开设备",
          () -> {
            workstation.disconnect();
            devicePage();
          });
    else
      action(
          "连接模拟设备",
          () ->
              progress(
                  "正在连接",
                  "正在连接 DEMO-P20…",
                  1000,
                  () -> {
                    workstation.connect();
                    home();
                  }));
  }

  private void newSample() {
    if (unsaved != null) {
      savePending();
      return;
    }
    page("样品测试", "登记样品信息", true);
    LinearLayout p = card();
    text(p, "选择毒品类型", 12, MUTED);
    Spinner drug = picker(p, false);
    text(p, "样品编号", 12, MUTED);
    EditText id = field(p, "请输入样品编号", "DEMO-" + System.currentTimeMillis() % 1000000);
    text(p, "仅使用虚构编号，结果单位为 ng/L。", 11, MUTED);
    action(
        "开始测试",
        () -> {
          if (!workstation.connected()) throw new IllegalStateException("请先连接设备");
          session =
              new DetectionSession(
                  id.getText().toString().trim(),
                  (DrugType) drug.getSelectedItem(),
                  accelerated ? 5000 : reactionMinutes * 60000L,
                  SystemClock::elapsedRealtime);
          step();
        });
  }

  private void step() {
    page("样品测试", session.drug.label + "  ·  " + session.sampleId, true);
    int stage = session.stage().ordinal();
    int index = stage == 0 ? 0 : stage == 1 ? 1 : stage == 2 ? 2 : stage == 3 ? 3 : 4;
    add(body, new Stepper(this, index), 61, 8);
    String[] hints = {
      "请插入电极试条，滴加电解质后开始活化。",
      "活化已完成，请滴加 10 μL 待测样品。",
      "保持设备静止，等待样品充分反应。",
      "反应已完成，请冲洗电极并添加反应液。",
      "正在分析样品，请勿关闭设备。"
    };
    info((index + 1) + "  " + hints[index]);
    LinearLayout p = card();
    text(p, "当前样品", 11, MUTED);
    text(p, session.drug.label, 16, INK);
    text(p, "样品编号   " + session.sampleId, 12, MUTED);
    switch (session.stage()) {
      case PREPARED:
        stepIllustration("device", "电极准备", "确认电极试条已插入检测口，并保持稳定。");
        action(
            "开始活化",
            () ->
                progress(
                    "电极活化中",
                    "正在执行模拟活化，请等待…",
                    1400,
                    () -> {
                      session.confirmElectrode();
                      step();
                    }));
        break;
      case SAMPLE_ADDED:
        stepIllustration("drop", "滴加样品", "将样品滴入试条电极区域，确认完成后开始反应计时。");
        action(
            "已加样，开始反应计时",
            () -> {
              session.confirmSample();
              step();
            });
        break;
      case REACTING:
        stepIllustration(
            "clock",
            "等待反应",
            accelerated
                ? "演示加速：5 秒代表实际 " + reactionMinutes + " 分钟。"
                : "常规反应时间：" + reactionMinutes + " 分钟。");
        TextView left = label("00:05", 32, BLUE);
        left.setGravity(Gravity.CENTER);
        add(body, left, 55, 10);
        Button next =
            button(
                "计时结束后继续",
                true,
                () -> {
                  session.finishReaction();
                  step();
                });
        next.setEnabled(false);
        add(footer, next, 46, 0);
        handler.post(
            new Runnable() {
              public void run() {
                if (session == null || session.stage() != DetectionSession.Stage.REACTING) return;
                long ms = session.remainingMillis(), seconds = (ms + 999) / 1000;
                left.setText(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
                next.setEnabled(ms == 0);
                if (ms > 0) handler.postDelayed(this, 200);
              }
            });
        break;
      case READY:
        stepIllustration("drop", "冲洗并添加反应液", "用清洁液冲洗电极，去除多余液体，再添加 50 μL 反应液。");
        action("开始检测", this::measure);
        break;
      default:
        break;
    }
    note("演示模式 · " + (accelerated ? "加速计时" : "常规计时"));
  }

  private void stepIllustration(String icon, String title, String description) {
    LinearLayout p = column(this);
    p.setGravity(Gravity.CENTER);
    p.setPadding(d(8), d(16), d(8), d(8));
    p.addView(new Icon(this, icon, MUTED), new LinearLayout.LayoutParams(d(44), d(44)));
    TextView name = label(title, 16, INK);
    name.setGravity(Gravity.CENTER);
    add(p, name, 45, 0);
    TextView message = label(description, 12, MUTED);
    message.setGravity(Gravity.CENTER);
    add(p, message, -2, 8);
    add(body, p, -2, 10);
  }

  private void progress(String title, String message, int millis, Runnable complete) {
    closeOverlay();
    Dialog dialog = new Dialog(this);
    overlay = dialog;
    LinearLayout p = panel(this);
    p.setPadding(d(24), d(24), d(24), d(22));
    p.setGravity(Gravity.CENTER);
    LoadingRing spinner = new LoadingRing(this);
    p.addView(spinner, new LinearLayout.LayoutParams(d(30), d(30)));
    TextView name = label(title, 16, INK);
    name.setTypeface(null, Typeface.BOLD);
    name.setGravity(Gravity.CENTER);
    add(p, name, 47, 0);
    TextView detail = label(message, 12, MUTED);
    detail.setGravity(Gravity.CENTER);
    add(p, detail, 35, 8);
    ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    bar.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
    add(p, bar, 5, 12);
    TextView caption = label("模拟采集 · 请勿关闭应用", 10, MUTED);
    caption.setGravity(Gravity.CENTER);
    p.addView(caption);
    dialog.setContentView(p);
    dialog.setCancelable(false);
    dialog.show();
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
      dialog
          .getWindow()
          .setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - d(64), d(340)), -2);
    }
    long start = SystemClock.elapsedRealtime();
    handler.post(
        new Runnable() {
          public void run() {
            if (overlay != dialog || isFinishing()) return;
            long elapsed = SystemClock.elapsedRealtime() - start;
            spinner.invalidate();
            bar.setProgress((int) Math.min(100, elapsed * 100 / millis));
            if (elapsed < millis) handler.postDelayed(this, 50);
            else {
              closeOverlay();
              safe(complete);
            }
          }
        });
  }

  private void measure() {
    session.beginMeasurement(workstation.connected());
    step();
    progress(
        "正在检测",
        "正在扫描并分析样品信号…",
        1600,
        () -> {
          unsaved =
              session.complete(
                  curves.get(session.drug),
                  workstation.baseline(),
                  workstation.sample(session.sampleId, session.drug),
                  System.currentTimeMillis());
          savePending();
        });
  }

  private void savePending() {
    try {
      repository.save(unsaved);
      DetectionResult saved = unsaved;
      unsaved = null;
      detail(saved, 0);
    } catch (IOException ex) {
      page("结果尚未保存", "请重试保存", true);
      info(ex.getMessage() + "；当前结果仅在内存中。");
      action("重试保存", this::savePending);
    }
  }

  private void tabs(String[] names, int selected, java.util.function.IntConsumer action) {
    LinearLayout row = new LinearLayout(this);
    for (int i = 0; i < names.length; i++) {
      final int index = i;
      TextView t = label(names[i], 12, i == selected ? BLUE : MUTED);
      t.setGravity(Gravity.CENTER);
      t.setPadding(0, d(11), 0, d(11));
      t.setBackground(shape(this, i == selected ? 0xffedf3ff : PALE, 0, 5));
      row.addView(t, new LinearLayout.LayoutParams(0, d(38), 1));
      t.setOnClickListener(v -> safe(() -> action.accept(index)));
    }
    add(body, row, 38, 15);
  }

  private void detail(DetectionResult r, int tab) {
    page("记录详情", r.sampleId + "  ·  " + r.drug.label, true);
    tabs(new String[] {"检测信息", "DPV 曲线", "计算详情"}, tab, i -> detail(r, i));
    LinearLayout p = card();
    if (tab == 0) {
      text(p, "模拟检测完成", 21, GREEN);
      text(p, String.format(Locale.ROOT, "%.3f ng/L", r.concentration), 28, INK);
      text(p, r.status(), 11, MUTED);
      text(
          p,
          "毒品类型    "
              + r.drug.label
              + "\n样品编号    "
              + r.sampleId
              + "\n检测时间    "
              + time.format(new Date(r.epochMillis)),
          12,
          INK);
      text(p, "✓ 已保存至本地记录", 11, GREEN);
    } else if (tab == 1) {
      text(p, "合成 DPV 信号", 13, INK);
      p.addView(new SignalChart(this, r.signal), new LinearLayout.LayoutParams(-1, d(225)));
      text(p, "电位 / V                   电流 / μA", 10, MUTED);
    } else {
      text(p, "峰电流计算", 14, INK);
      text(
          p,
          String.format(
              Locale.ROOT,
              "基线峰 I0    %.5f μA\n样品峰 I1    %.5f μA\n峰电流差 ΔI    %.5f μA",
              r.baselinePeak,
              r.samplePeak,
              r.samplePeak - r.baselinePeak),
          13,
          INK);
      text(p, r.calibration.equation(), 12, BLUE);
      text(
          p,
          "适用区间 "
              + r.calibration.min
              + "–"
              + r.calibration.max
              + " ng/L\n标曲 ID："
              + r.calibration.id,
          10,
          MUTED);
    }
    note("合成数据 · 不作为真实检测结论");
    action("导出检测数据", () -> export(Collections.singletonList(r)));
    add(footer, button("历史记录", false, () -> history("", null)), 40, 0);
  }

  private void history(String query, DrugType filter) {
    page("历史记录", "检测结果查询与导出", true);
    Set<String> selected = new HashSet<>();
    LinearLayout controls = card();
    EditText search = field(controls, "搜索样品编号", query);
    Spinner drug = picker(controls, true);
    if (filter != null) drug.setSelection(filter.ordinal() + 1);
    add(
        controls,
        button(
            "查询",
            false,
            () ->
                history(
                    search.getText().toString().trim(),
                    drug.getSelectedItem() instanceof DrugType
                        ? (DrugType) drug.getSelectedItem()
                        : null)),
        36,
        0);
    try {
      List<DetectionResult> rows = repository.search(query, filter);
      text(body, "共 " + rows.size() + " 条记录  ·  合成数据", 11, MUTED);
      if (rows.isEmpty()) stepIllustration("records", "暂无检测记录", "完成一次样品测试后，结果将显示在这里。");
      for (DetectionResult r : rows) {
        LinearLayout item = card();
        CheckBox check = new CheckBox(this);
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));
        check.setText(r.sampleId);
        check.setTextSize(13);
        check.setTextColor(INK);
        item.addView(check);
        check.setOnCheckedChangeListener(
            (v, value) -> {
              if (value) selected.add(r.id);
              else selected.remove(r.id);
            });
        item.setOnLongClickListener(
            v -> {
              check.setChecked(!check.isChecked());
              return true;
            });
        text(
            item,
            r.drug.label + "  ·  " + String.format(Locale.ROOT, "%.3f ng/L", r.concentration),
            12,
            INK);
        text(item, time.format(new Date(r.epochMillis)), 11, MUTED);
        LinearLayout row = new LinearLayout(this);
        row.addView(
            button("查看详情", false, () -> detail(r, 0)), new LinearLayout.LayoutParams(0, d(35), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, d(35), 1);
        lp.leftMargin = d(8);
        row.addView(button("导出", false, () -> export(Collections.singletonList(r))), lp);
        item.addView(row);
      }
      action(
          "导出所选记录",
          () -> {
            List<DetectionResult> chosen = new ArrayList<>();
            for (DetectionResult r : rows) if (selected.contains(r.id)) chosen.add(r);
            export(chosen);
          });
    } catch (IOException ex) {
      info("读取失败：" + ex.getMessage());
    }
  }

  private void export(List<DetectionResult> rows) {
    if (rows.isEmpty()) throw new IllegalArgumentException("请先勾选至少一条记录");
    pendingExport = new ArrayList<>(rows);
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.putExtra(Intent.EXTRA_TITLE, "SIMULATED-results-" + System.currentTimeMillis() + ".xlsx");
    startActivityForResult(i, EXPORT);
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
                toast("检测数据已导出（模拟）");
              } catch (IOException ex) {
                throw new IllegalStateException("导出失败：" + ex.getMessage(), ex);
              }
            });
      pendingExport = null;
    }
  }

  private void authorization() {
    closeOverlay();
    Dialog dialog = new Dialog(this);
    overlay = dialog;
    LinearLayout p = panel(this);
    p.setPadding(d(20), d(18), d(20), d(16));
    TextView title = label("管理员授权", 17, INK);
    title.setGravity(Gravity.CENTER);
    add(p, title, 30, 4);
    TextView sub = label("请输入演示口令", 11, MUTED);
    sub.setGravity(Gravity.CENTER);
    add(p, sub, 23, 8);
    TextView dots = label("○   ○   ○   ○   ○   ○", 22, BLUE);
    dots.setGravity(Gravity.CENTER);
    add(p, dots, 40, 10);
    StringBuilder code = new StringBuilder();
    for (String[] row :
        new String[][] {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"删除", "0", "确认"}}) {
      LinearLayout line = new LinearLayout(this);
      for (String key : row) {
        Button b =
            button(
                key,
                key.equals("确认"),
                () -> {
                  if (key.equals("删除")) {
                    if (code.length() > 0) code.deleteCharAt(code.length() - 1);
                  } else if (key.equals("确认")) {
                    if (code.toString().equals("123456")) {
                      closeOverlay();
                      calibrationManager();
                      return;
                    }
                    code.setLength(0);
                    toast("演示口令为 123456");
                  } else if (code.length() < 6) code.append(key);
                  StringBuilder mask = new StringBuilder();
                  for (int n = 0; n < 6; n++) mask.append(n < code.length() ? "●   " : "○   ");
                  dots.setText(mask.toString().trim());
                });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, d(46), 1);
        lp.setMargins(d(3), d(3), d(3), d(3));
        line.addView(b, lp);
      }
      p.addView(line);
    }
    TextView hint = label("演示口令：123456，仅用于界面展示", 10, MUTED);
    hint.setGravity(Gravity.CENTER);
    add(p, hint, 30, 0);
    dialog.setContentView(p);
    dialog.show();
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
      dialog
          .getWindow()
          .setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - d(50), d(340)), -2);
    }
  }

  private void calibrationManager() {
    page("标准曲线管理", "内置演示参数", true);
    for (DrugType drug : DrugType.values()) {
      LinearLayout p = card();
      text(p, drug.label, 14, INK);
      text(
          p, "来源：" + (curves.get(drug).id.startsWith("DEMO-FIXTURE") ? "内置演示" : "本地拟合"), 11, MUTED);
      add(p, button("查看 / 绘制标曲", false, () -> calibration(drug)), 37, 0);
    }
    note("标曲为合成样例，不代表仪器性能");
  }

  private void calibration(DrugType drug) {
    page("标准曲线", drug.label + "  ·  演示标定", true);
    LinearLayout curve = card();
    text(curve, curves.get(drug).equation(), 12, BLUE);
    FrameLayout plot = new FrameLayout(this);
    curve.addView(plot, new LinearLayout.LayoutParams(-1, d(180)));
    plot.addView(new SignalChart(this, curves.get(drug).fittedLine(), "ng/L"));
    LinearLayout inputs = card();
    text(inputs, "浓度（ng/L，5–10 个递增值）", 11, MUTED);
    EditText x = field(inputs, "用英文逗号分隔", "1,10,25,50,100");
    text(inputs, "峰电流 I0 到 In（μA）", 11, MUTED);
    EditText peaks = field(inputs, "用英文逗号分隔", "1,1.28,2.28,4.48,8.68,16.88");
    action(
        "生成并保存曲线",
        () -> {
          saveCurve(Calibration.fit(drug, numbers(x), numbers(peaks)));
          calibration(drug);
          toast("演示标曲已保存");
        });
    add(
        footer,
        button(
            "恢复默认标曲",
            false,
            () -> {
              saveCurve(Calibration.demo(drug));
              calibration(drug);
            }),
        36,
        0);
  }

  private double[] numbers(EditText input) {
    String[] parts = input.getText().toString().split(",", -1);
    double[] n = new double[parts.length];
    for (int i = 0; i < n.length; i++) n[i] = Double.parseDouble(parts[i].trim());
    return n;
  }

  private void settings() {
    page("系统设置", "设备与检测参数", true);
    LinearLayout p = card();
    text(p, "检测设置", 13, MUTED);
    add(p, button("标准曲线管理   ›", false, this::authorization), 45, 8);
    add(
        p,
        button(
            "反应时间    " + reactionMinutes + " 分钟   ›",
            false,
            () -> {
              reactionMinutes = reactionMinutes == 5 ? 10 : 5;
              prefs().edit().putInt("minutes", reactionMinutes).apply();
              settings();
            }),
        45,
        8);
    Switch speed = new Switch(this);
    speed.setText("演示加速计时（5 秒）");
    speed.setTextSize(13);
    speed.setChecked(accelerated);
    p.addView(speed);
    speed.setOnCheckedChangeListener(
        (v, value) -> {
          accelerated = value;
          prefs().edit().putBoolean("accelerated", value).apply();
        });
    LinearLayout system = card();
    text(system, "系统信息", 13, MUTED);
    text(system, "当前时间    " + time.format(new Date()), 12, INK);
    Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    text(
        system,
        "终端电量    " + (level >= 0 && scale > 0 ? 100 * level / scale + "%" : "不可用"),
        12,
        INK);
    text(system, "版本    0.2.0 公开演示版", 12, INK);
    note("时间与电量来自运行终端");
  }

  @Override
  protected void onDestroy() {
    abandon();
    workstation.disconnect();
    repository.close();
    super.onDestroy();
  }
}
