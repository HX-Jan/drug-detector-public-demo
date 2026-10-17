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

/** 依据参考图组织的触屏界面；业务计算与数据存储共用 core。 */
public final class MainActivity extends Activity {
  private static final int EXPORT = 100;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Workstation workstation = new SimulatedWorkstation();
  CurveLibrary library;
  private Calibration selectedCalibration;
  private boolean administrator;
  private StandardCurve pendingCurveExport;
  private static final int CURVE_EXPORT = 101;
  private SqliteResultRepository repository;
  LinearLayout body, footer;
  private DetectionSession session;
  private int reactionMinutes = 5;
  private List<DetectionResult> pendingExport;
  private DetectionResult unsaved;
  Dialog overlay;
  private final SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    repository = new SqliteResultRepository(this);
    reactionMinutes = prefs().getInt("minutes", 5);
    try {
      library = new CurveLibrary(new File(getFilesDir(), "curves").toPath());
      DemoCatalog.ensure(library);
      workstation.connect();
    } catch (IOException ex) {
      throw new IllegalStateException("标曲库读取失败", ex);
    }
    home();
    if (state != null)
      toast("未完成的流程已取消，已保存记录可在历史记录中查看");
  }

  private SharedPreferences prefs() {
    return getSharedPreferences("app-settings", MODE_PRIVATE);
  }

  int d(int n) {
    return dp(this, n);
  }

  TextView label(String value, int size, int color) {
    return ReferenceUi.label(this, value, size, color);
  }

  void add(LinearLayout parent, View child, int height, int bottom) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,
        height < 0 ? height : d(height));
    lp.bottomMargin = d(bottom);
    parent.addView(child, lp);
  }

  void text(LinearLayout parent, String value, int size, int color) {
    add(parent, label(value, size, color), -2, 10);
  }

  Button button(String value, boolean primary, Runnable action) {
    return ReferenceUi.action(this, value, primary, () -> safe(action));
  }

  private void action(String value, Runnable action) {
    add(footer, button(value, true, action), 46, 0);
  }

  LinearLayout card() {
    LinearLayout p = panel(this);
    add(body, p, -2, 12);
    return p;
  }

  void page(String title, String subtitle, boolean back) {
    closeOverlay();
    LinearLayout root = column(this);
    root.setBackgroundColor(Color.WHITE);
    if (Build.VERSION.SDK_INT >= 30)
      root.setOnApplyWindowInsetsListener((v, insets) -> {
        Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
        return insets;
      });
    else
      root.setFitsSystemWindows(true);
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
    if (all)
      options.add("全部类型");
    options.addAll(Arrays.asList(DrugType.values()));
    ArrayAdapter<Object> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
        options);
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

  void safe(Runnable action) {
    try {
      action.run();
    } catch (Exception ex) {
      new AlertDialog.Builder(this).setTitle("操作未完成").setMessage(ex.getMessage())
          .setPositiveButton("知道了", null).show();
    }
  }

  void toast(String value) {
    Toast.makeText(this, value, Toast.LENGTH_LONG).show();
  }

  void closeOverlay() {
    if (overlay != null) {
      overlay.dismiss();
      overlay = null;
    }
  }

  private void abandon() {
    handler.removeCallbacksAndMessages(null);
    closeOverlay();
    if (session != null)
      session.cancel();
    session = null;
  }

  private void home() {
    abandon();
    page("毒品快速检测仪", "电化学检测终端", false);
    LinearLayout connection = card();
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView dot = label("●", 15, workstation.connected() ? GREEN : MUTED);
    row.addView(dot, new LinearLayout.LayoutParams(d(22), -2));
    LinearLayout summary = column(this);
    summary.addView(label(workstation.connected() ? "设备已连接，就绪" : "设备未连接", 13, INK));
    summary.addView(label(workstation.connected() ? "工作站已连接" : "工作站未连接", 10, MUTED));
    row.addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(label(workstation.connected() ? "已连接  ›" : "未连接  ›", 11,
        workstation.connected() ? GREEN : MUTED));
    connection.addView(row);
    connection.setOnClickListener(v -> devicePage());
    Button start = button(workstation.connected() ? "开始样品测试" : "连接设备", true,
        workstation.connected() ? this::newSample : this::devicePage);
    add(body, start, 52, 4);
    TextView hint = label(workstation.connected() ? "活化 · 加样 · 反应 · 扫描 · 分析" : "连接后可开始样品测试", 10,
        MUTED);
    hint.setGravity(Gravity.CENTER);
    add(body, hint, 23, 15);
    LinearLayout shortcuts = new LinearLayout(this);
    String[] names = {"样品测试", "历史记录", "系统设置"}, icons = {"device", "records", "settings"};
    Runnable[] actions = {this::newSample, () -> history("", null), this::settings};
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
      if (i > 0)
        lp.leftMargin = d(8);
      shortcuts.addView(tile, lp);
      tile.setOnClickListener(v -> safe(run));
      if (i == 0) {
        tile.setEnabled(workstation.connected());
        if (!workstation.connected())
          tile.setAlpha(.45f);
      }
    }
    add(body, shortcuts, -2, 12);
    if (!workstation.connected())
      info("样品测试需先连接设备");
    int count = 0;
    try {
      String today = time.format(new Date()).substring(0, 10);
      for (DetectionResult r : repository.all())
        if (time.format(new Date(r.epochMillis)).startsWith(today))
          count++;
    } catch (IOException ignored) {
    }
    note("请先连接设备，再开始样品测试");
    LinearLayout stats = new LinearLayout(this);
    stats.addView(label("今日已检测 " + count + " 例", 10, MUTED),
        new LinearLayout.LayoutParams(0, -2, 1));
    TextView today = label(new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date()), 10,
        MUTED);
    today.setGravity(Gravity.RIGHT);
    stats.addView(today);
    add(footer, stats, 18, 0);
  }

  private void devicePage() {
    page("设备连接", workstation.connected() ? "内置模拟设备已就绪" : "内置模拟设备未连接", true);
    LinearLayout p = card();
    if (workstation.connected()) {
      text(p, "● 已连接设备", 16, GREEN);
      text(p, "设备通信正常\n可直接开始样品测试", 12, MUTED);
      add(body, button("断开连接", false, () -> {
        workstation.disconnect();
        devicePage();
      }), 40, 12);
      info("✓ 设备就绪，可以开始样品测试。检测过程中请保持设备稳定。");
      action("开始样品测试", this::newSample);
    } else {
      text(p, "展示版使用内置模拟设备，无需连接真实硬件。", 12, MUTED);
      add(footer, button("重新连接内置模拟设备", true, () -> {
        workstation.connect();
        devicePage();
      }), 40, 8);
    }
  }

  private void newSample() {
    if (!workstation.connected())
      throw new IllegalStateException("请先连接设备");
    if (unsaved != null) {
      savePending();
      return;
    }
    page("样品测试", workstation.connected() ? "设备已连接" : "设备未连接", true);
    add(body, new Stepper(this, 0), 61, 8);
    info("请准备进行样品测试。");
    LinearLayout p = card();
    text(p, "选择毒品类型", 12, MUTED);
    Spinner drug = new Spinner(this);
    drug.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, library.all()));
    add(p, drug, 42, 10);
    text(p, "样品编号", 12, MUTED);
    EditText id = field(p, "请输入样品编号", "");
    Button demo = button("加载内置测试样本", false, () -> {
      DemoSample sample = DemoSample.standard();
      id.setText(sample.sampleId());
      for (int i = 0; i < drug.getCount(); i++) {
        StandardCurve curve = (StandardCurve) drug.getItemAtPosition(i);
        if (curve.drug == sample.drug()) { drug.setSelection(i); break; }
      }
    });
    add(p, demo, 40, 4);
    text(p, "样品编号将保存到检测记录。内置样本仅生成合成信号。", 11, MUTED);
    action("开始活化", () -> {
      if (!workstation.connected())
        throw new IllegalStateException("请先连接设备");
      StandardCurve chosen = (StandardCurve) drug.getSelectedItem();
      if (chosen == null)
        throw new IllegalStateException("请先由管理员保存一条标准曲线");
      selectedCalibration = chosen.fit();
      session = new DetectionSession(id.getText().toString().trim(), chosen.drug,
          reactionMinutes * 60000L, SystemClock::elapsedRealtime);
      step();
      try {
        workstation.baseline();
      } catch (RuntimeException ex) {
        toast(ex.getMessage());
        return;
      }
      session.confirmElectrode();
      step();
    });
  }

  private String remainingText(DetectionSession session) {
    long ms = session.remainingMillis(), seconds = (ms + 999) / 1000;
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
  }

  private void step() {
    page("样品测试", session.drug.label + "  ·  " + session.sampleId, true);
    int stage = session.stage().ordinal();
    int index = stage == 0 ? 0 : stage == 1 ? 1 : stage == 2 ? 2 : stage == 3 ? 3 : 4;
    add(body, new Stepper(this, index), 61, 8);
    String[] hints = {"请插入电极试条，滴加电解质后开始活化。", "活化已完成，请滴加 10 μL 待测样品。", "保持设备静止，等待样品充分反应。",
        "反应已完成，请冲洗电极并添加反应液。", "正在分析样品，请勿关闭设备。"};
    info((index + 1) + "  " + hints[index]);
    LinearLayout p = card();
    text(p, "当前样品", 11, MUTED);
    text(p, session.drug.label, 16, INK);
    text(p, "样品编号   " + session.sampleId, 12, MUTED);
    switch (session.stage()) {
      case PREPARED :
        stepIllustration("device", "电极准备", "确认电极试条已插入检测口，并保持稳定。");
        action("开始活化", () -> {
          try {
            workstation.baseline();
          } catch (RuntimeException ex) {
            toast(ex.getMessage());
            return;
          }
          session.confirmElectrode();
          step();
        });
        break;
      case SAMPLE_ADDED :
        stepIllustration("drop", "滴加样品", "将样品滴入试条电极区域，确认完成后开始反应计时。");
        action("已加样，开始反应计时", () -> {
          session.confirmSample();
          step();
        });
        break;
      case REACTING :
        stepIllustration("clock", "等待反应",
            "常规反应时间：" + reactionMinutes + " 分钟。");
        TextView left = label(remainingText(session), 32, BLUE);
        left.setGravity(Gravity.CENTER);
        add(body, left, 55, 10);
        Button next = button("计时结束后继续", true, () -> {
          session.finishReaction();
          step();
        });
        next.setEnabled(false);
        add(footer, next, 46, 0);
        add(footer, button("跳过计时（展示模式）", false, () -> {
          session.skipReaction();
          step();
        }), 42, 8);
        handler.post(new Runnable() {
          public void run() {
            if (session == null || session.stage() != DetectionSession.Stage.REACTING)
              return;
            long ms = session.remainingMillis(), seconds = (ms + 999) / 1000;
            left.setText(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
            next.setEnabled(ms == 0);
            if (ms > 0)
              handler.postDelayed(this, 200);
          }
        });
        break;
      case READY :
        stepIllustration("drop", "冲洗并添加反应液", "用清洁液冲洗电极，去除多余液体，再添加 50 μL 反应液。");
        action("开始检测", this::measure);
        break;
      default :
        break;
    }
    note("常规计时 · 反应阶段请保持设备稳定");
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

  void progress(String title, String message, int millis, Runnable complete) {
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
    TextView caption = label("数据采集中 · 请勿关闭应用", 10, MUTED);
    caption.setGravity(Gravity.CENTER);
    p.addView(caption);
    dialog.setContentView(p);
    dialog.setCancelable(false);
    dialog.show();
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
      dialog.getWindow()
          .setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - d(64), d(340)), -2);
    }
    long start = SystemClock.elapsedRealtime();
    handler.post(new Runnable() {
      public void run() {
        if (overlay != dialog || isFinishing())
          return;
        long elapsed = SystemClock.elapsedRealtime() - start;
        spinner.invalidate();
        bar.setProgress((int) Math.min(100, elapsed * 100 / millis));
        if (elapsed < millis)
          handler.postDelayed(this, 50);
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
    progress("正在检测", "正在扫描并分析样品信号…", 1600, () -> {
      unsaved = session.complete(selectedCalibration, workstation.baseline(),
          workstation.sample(session.sampleId, session.drug), System.currentTimeMillis());
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
    tabs(new String[]{"检测信息", "DPV 曲线", "计算详情"}, tab, i -> detail(r, i));
    LinearLayout p = card();
    if (tab == 0) {
      text(p, "检测完成", 21, GREEN);
      text(p, String.format(Locale.ROOT, "%.3f ng/L", r.concentration), 28, INK);
      text(p, r.status(), 11, MUTED);
      text(p, "毒品类型    " + r.drug.label + "\n样品编号    " + r.sampleId + "\n检测时间    "
          + time.format(new Date(r.epochMillis)), 12, INK);
      text(p, "✓ 已保存至本地记录", 11, GREEN);
    } else if (tab == 1) {
      text(p, "DPV 信号", 13, INK);
      p.addView(new SignalChart(this, r.signal), new LinearLayout.LayoutParams(-1, d(225)));
      text(p, "电位 / V                   电流 / μA", 10, MUTED);
    } else {
      text(p, "峰电流计算", 14, INK);
      text(p, String.format(Locale.ROOT, "基线峰 I0    %.5f μA\n样品峰 I1    %.5f μA\n峰电流差 ΔI    %.5f μA",
          r.baselinePeak, r.samplePeak, r.samplePeak - r.baselinePeak), 13, INK);
      text(p, r.calibration.equation(), 12, BLUE);
      text(p, "适用区间 " + r.calibration.min + "–" + r.calibration.max + " ng/L\n标曲 ID："
          + r.calibration.id, 10, MUTED);
    }
    note("结果保存于本地记录 · 可导出存档");
    action("导出检测数据", () -> export(Collections.singletonList(r)));
    add(footer, button("历史记录", false, () -> history("", null)), 40, 0);
  }

  private void history(String query, DrugType filter) {
    page("历史记录", "检测结果查询与导出", true);
    Set<String> selected = new HashSet<>();
    LinearLayout controls = card();
    EditText search = field(controls, "搜索样品编号", query);
    Spinner drug = picker(controls, true);
    if (filter != null)
      drug.setSelection(filter.ordinal() + 1);
    add(controls,
        button("查询", false, () -> history(search.getText().toString().trim(),
            drug.getSelectedItem() instanceof DrugType ? (DrugType) drug.getSelectedItem() : null)),
        36, 0);
    try {
      List<DetectionResult> rows = repository.search(query, filter);
      text(body, "共 " + rows.size() + " 条记录", 11, MUTED);
      if (rows.isEmpty())
        stepIllustration("records", "暂无检测记录", "完成一次样品测试后，结果将显示在这里。");
      for (DetectionResult r : rows) {
        LinearLayout item = card();
        CheckBox check = new CheckBox(this);
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));
        check.setText(r.sampleId);
        check.setTextSize(13);
        check.setTextColor(INK);
        item.addView(check);
        check.setOnCheckedChangeListener((v, value) -> {
          if (value)
            selected.add(r.id);
          else
            selected.remove(r.id);
        });
        item.setOnLongClickListener(v -> {
          check.setChecked(!check.isChecked());
          return true;
        });
        text(item,
            r.drug.label + "  ·  " + String.format(Locale.ROOT, "%.3f ng/L", r.concentration), 12,
            INK);
        text(item, time.format(new Date(r.epochMillis)), 11, MUTED);
        LinearLayout row = new LinearLayout(this);
        row.addView(button("查看详情", false, () -> detail(r, 0)),
            new LinearLayout.LayoutParams(0, d(35), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, d(35), 1);
        lp.leftMargin = d(8);
        row.addView(button("导出", false, () -> export(Collections.singletonList(r))), lp);
        item.addView(row);
      }
      action("导出所选记录", () -> {
        List<DetectionResult> chosen = new ArrayList<>();
        for (DetectionResult r : rows)
          if (selected.contains(r.id))
            chosen.add(r);
        export(chosen);
      });
    } catch (IOException ex) {
      info("读取失败：" + ex.getMessage());
    }
  }

  private void export(List<DetectionResult> rows) {
    if (rows.isEmpty())
      throw new IllegalArgumentException("请先勾选至少一条记录");
    pendingExport = new ArrayList<>(rows);
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.putExtra(Intent.EXTRA_TITLE, "检测记录-" + System.currentTimeMillis() + ".xlsx");
    startActivityForResult(i, EXPORT);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == CURVE_EXPORT) {
      if (result == RESULT_OK && data != null && data.getData() != null
          && pendingCurveExport != null)
        safe(() -> {
          try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null)
              throw new IOException("无法打开目标文件");
            CurveExport.xlsx(pendingCurveExport, out);
            toast("标曲 Excel 已导出到所选位置");
          } catch (IOException ex) {
            throw new IllegalStateException("导出失败：" + ex.getMessage(), ex);
          }
        });
      pendingCurveExport = null;
      return;
    }
    if (request == EXPORT) {
      if (result == RESULT_OK && data != null && data.getData() != null && pendingExport != null)
        safe(() -> {
          try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null)
              throw new IOException("无法打开目标文件");
            XlsxExporter.write(pendingExport, out);
            toast("检测数据已导出");
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
    TextView sub = label("请输入管理员口令", 11, MUTED);
    sub.setGravity(Gravity.CENTER);
    add(p, sub, 23, 8);
    TextView dots = label("○   ○   ○   ○   ○   ○", 22, BLUE);
    dots.setGravity(Gravity.CENTER);
    add(p, dots, 40, 10);
    StringBuilder code = new StringBuilder();
    for (String[] row : new String[][]{{"1", "2", "3", "删除"}, {"4", "5", "6", "清除"},
        {"7", "8", "9", "取消"}, {"", "0", "", "确认"}}) {
      LinearLayout line = new LinearLayout(this);
      for (String key : row) {
        Button b = button(key, key.equals("确认"), () -> {
          if (key.equals("取消")) {
            closeOverlay();
            return;
          }
          if (key.equals("清除")) {
            code.setLength(0);
          } else if (key.equals("删除")) {
            if (code.length() > 0)
              code.deleteCharAt(code.length() - 1);
          } else if (key.equals("确认")) {
            if (code.length() == 6) {
              closeOverlay();
              administrator = true;
              settings();
              return;
            }
            code.setLength(0);
            toast("口令不匹配，请重新输入");
          } else if (!key.isEmpty() && code.length() < 6)
            code.append(key);
          StringBuilder mask = new StringBuilder();
          for (int n = 0; n < 6; n++)
            mask.append(n < code.length() ? "●   " : "○   ");
          dots.setText(mask.toString().trim());
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, d(46), 1);
        lp.setMargins(d(3), d(3), d(3), d(3));
        line.addView(b, lp);
      }
      p.addView(line);
    }
    TextView hint = label("管理员口令由运维在部署时配置", 10, MUTED);
    hint.setGravity(Gravity.CENTER);
    add(p, hint, 30, 0);
    dialog.setContentView(p);
    dialog.show();
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
      dialog.getWindow()
          .setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - d(50), d(340)), -2);
    }
  }

  /** 标曲保存于独立库，列表选择不会立即修改活动检测所用的标曲快照。 */
  void calibrationManager() {
    if (!administrator)
      throw new IllegalStateException("请先开启管理员模式");
    page("标准曲线管理", "共 " + library.all().size() + " 条曲线", true);
    StandardCurve[] selected = {null};
    text(body, "名称                                  来源", 12, MUTED);
    RadioGroup group = new RadioGroup(this);
    for (StandardCurve curve : library.all()) {
      RadioButton row = new RadioButton(this);
      row.setId(View.generateViewId());
      row.setText(curve.name + "                         "
          + "本地标定");
      row.setTextSize(13);
      row.setTextColor(INK);
      row.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));
      row.setOnClickListener(v -> selected[0] = curve);
      group.addView(row, new LinearLayout.LayoutParams(-1, d(46)));
    }
    add(body, group, -2, 10);
    note("已保存的标曲可在样品测试中选择");
    LinearLayout row = new LinearLayout(this);
    String[] labels = {"添加", "修改", "删除"};
    Runnable[] actions = {() -> new AndroidCurveEditor(this, new StandardCurve()).start(), () -> {
      if (selected[0] == null)
        throw new IllegalStateException("请先选中一条标曲");
      new AndroidCurveEditor(this, selected[0]).start();
    }, () -> {
      if (selected[0] == null)
        throw new IllegalStateException("请先选中一条标曲");
      new AndroidCurveEditor(this, selected[0]).confirm("删除标准曲线",
          "确认删除标准曲线「" + selected[0].name + "」？\n删除后不可恢复，已保存的检测记录不受影响。", () -> {
            try {
              library.delete(selected[0].id);
              calibrationManager();
            } catch (IOException e) {
              throw new IllegalStateException("删除失败", e);
            }
          });
    }};
    for (int i = 0; i < 3; i++) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, d(46), 1);
      if (i > 0)
        lp.leftMargin = d(8);
      row.addView(button(labels[i], i == 0, actions[i]), lp);
    }
    add(footer, row, 46, 0);
  }

  /** U 盘由系统文档选择器授权，不申请所有文件访问权限，取消时不提示成功。 */
  void exportCurveToDocument(StandardCurve curve) {
    pendingCurveExport = curve;
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.putExtra(Intent.EXTRA_TITLE, curve.name + "-标准曲线.xlsx");
    toast("请在系统文件选择器中选择已挂载的 U 盘位置");
    startActivityForResult(i, CURVE_EXPORT);
  }

  private void settings() {
    page("系统设置", "设备与检测参数", true);
    LinearLayout p = card();
    text(p, "管理员模式", 14, INK);
    text(p, "开启后可修改检测参数、维护标准曲线等功能。", 12, MUTED);
    Button enable = button(administrator ? "管理员模式已开启" : "开启管理员模式", !administrator,
        this::authorization);
    enable.setEnabled(!administrator);
    add(p, enable, 45, 8);
    LinearLayout maintenance = card();
    text(maintenance, "维护入口", 13, INK);
    Button manage = button("标准曲线管理", false, this::calibrationManager);
    manage.setEnabled(administrator);
    LinearLayout entries = new LinearLayout(this);
    entries.addView(manage, new LinearLayout.LayoutParams(0, d(45), 1));
    LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(0, d(45), 1);
    timeLp.leftMargin = d(8);
    entries.addView(
        button("调整时间", false,
            () -> startActivity(new Intent(android.provider.Settings.ACTION_DATE_SETTINGS))),
        timeLp);
    add(maintenance, entries, 45, 8);
    if (administrator)
      add(maintenance, button("诊断", true, this::diagnostics), 45, 8);
    LinearLayout system = card();
    text(system, "系统信息", 13, MUTED);
    text(system, "当前时间    " + time.format(new Date()), 12, INK);
    Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    text(system, "终端电量    " + (level >= 0 && scale > 0 ? 100 * level / scale + "%" : "不可用"), 12,
        INK);
    text(system, "版本    1.0.0", 12, INK);
    note("时间与电量来自运行终端");
  }

  /** 将运行状态与计时选项放入诊断弹窗，主设置页遵循截图的维护入口布局。 */
  private void diagnostics() {
    LinearLayout p = column(this);
    p.setPadding(d(20), d(12), d(20), d(12));
    text(p, "工作站状态：" + (workstation.connected() ? "已连接" : "未连接") + "\n标曲数量：" + library.all().size(),
        12, INK);
    Switch speed = new Switch(this);
    Spinner duration = new Spinner(this);
    duration.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
        new String[]{"反应时间 5 分钟", "反应时间 10 分钟"}));
    duration.setSelection(reactionMinutes == 5 ? 0 : 1);
    p.addView(duration);
    new AlertDialog.Builder(this).setTitle("诊断与运行参数").setView(p).setNegativeButton("取消", null)
        .setPositiveButton("确认", (dialog, which) -> {
          reactionMinutes = duration.getSelectedItemPosition() == 0 ? 5 : 10;
          prefs().edit().putInt("minutes", reactionMinutes)
              .apply();
        }).show();
  }

  @Override
  protected void onDestroy() {
    abandon();
    workstation.disconnect();
    repository.close();
    super.onDestroy();
  }
}
