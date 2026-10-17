package org.example.detector.core;

import java.util.*;
import java.util.function.*;

/** 按参考图片顺序驱动标曲五页。View 仅负责原生控件绘制、键盘和异步进度， 数据校验、步骤门禁及保存逻辑集中在此处，保证电脑与 Android 行为一致。 */
public final class CurveWorkflow {
  public interface View {
    void page(String title, String subtitle, String[] tabs, int selected, IntConsumer change);

    void text(String value, boolean muted);

    void field(String label, String value, boolean alphabet, Consumer<String> changed);

    void range(
        String label,
        double start,
        double end,
        DoubleConsumer startChanged,
        DoubleConsumer endChanged);

    void baseline(String label, Runnable rescan);

    void choice(String label, String[] options, int selected, IntConsumer changed);

    void buttons(String[] labels, boolean[] primary, Runnable[] actions, boolean footer);

    void plot(Signal signal, String unit, String title);

    void progress(String title, String message, int count, IntConsumer acquire, Runnable complete);

    void confirm(String title, String message, Runnable confirmed);

    void message(String message);

    void details(String title, String text);

    void export(StandardCurve curve, boolean usb);

    String destination();
  }

  private final View view;
  private final CurveLibrary library;
  private final Runnable saved;
  private final Workstation workstation;
  public final StandardCurve curve;
  private int tab;
  private double concentration = 1;
  private String status = "等待导出";
  private static final String[] TABS = {"信息", "活化", "曲线", "LOD/LOQ", "导出"};

  public CurveWorkflow(View view, CurveLibrary library, StandardCurve curve, Runnable saved) {
    this(view, library, curve, saved, demoWorkstation());
  }

  /** 展示版默认使用内置设备；真实设备适配器通过带 workstation 参数的构造函数注入。 */
  private static Workstation demoWorkstation() {
    SimulatedWorkstation device = new SimulatedWorkstation();
    device.connect();
    return device;
  }

  public CurveWorkflow(
      View view,
      CurveLibrary library,
      StandardCurve curve,
      Runnable saved,
      Workstation workstation) {
    this.view = view;
    this.library = library;
    this.curve = curve;
    this.saved = saved;
    this.workstation = workstation;
  }

  private void buttons(
      boolean footer, String a, boolean ap, Runnable ar, String b, boolean bp, Runnable br) {
    view.buttons(new String[] {a, b}, new boolean[] {ap, bp}, new Runnable[] {ar, br}, footer);
  }

  private void single(boolean footer, String title, Runnable action) {
    view.buttons(new String[] {title}, new boolean[] {true}, new Runnable[] {action}, footer);
  }

  /** 点击顶部标签也要检查前置数据，不能绕过采集与统计步骤。 */
  public void show(int index) {
    if (index > 0) curve.validateInfo();
    if (index > 1) curve.validateActivation();
    if (index > 2) curve.fit();
    if (index > 3) curve.validateSave();
    tab = index;
    view.page(
        "添加标准曲线",
        workstation.origin().label + " · 名称  " + (curve.name.isEmpty() ? "尚未填写" : curve.name),
        TABS,
        tab,
        this::show);
    switch (tab) {
      case 0:
        information();
        break;
      case 1:
        activation();
        break;
      case 2:
        points();
        break;
      case 3:
        lod();
        break;
      case 4:
        exports();
        break;
      default:
        throw new IllegalArgumentException("未知页面");
    }
  }

  private void parameter(String title, double value, DoubleConsumer change, boolean invalidate) {
    view.field(
        title,
        StandardCurve.number(value),
        false,
        s -> {
          double d = StandardCurve.parseNumber(s);
          StandardCurve.finite(d);
          change.accept(d);
          if (invalidate) curve.invalidateAcquisition();
          show(tab);
        });
  }

  private void information() {
    view.choice(
        "检测类型",
        Arrays.stream(DrugType.values()).map(d -> d.label).toArray(String[]::new),
        curve.drug.ordinal(),
        i -> {
          curve.drug = DrugType.values()[i];
          curve.invalidateAcquisition();
          show(0);
        });
    view.field(
        "英文名",
        curve.name,
        true,
        s -> {
          if (!s.matches("[A-Za-z][A-Za-z0-9_-]{0,23}"))
            throw new IllegalArgumentException("请输入有效英文名");
          curve.name = s;
          show(0);
        });
    // 图片中两个范围各占一行；宿主将带“至”的参数对排成双列。
    view.range(
        "扫描范围",
        curve.scanStart,
        curve.scanEnd,
        v -> {
          curve.scanStart = v;
          curve.invalidateAcquisition();
          show(0);
        },
        v -> {
          curve.scanEnd = v;
          curve.invalidateAcquisition();
          show(0);
        });
    view.range(
        "寻峰范围",
        curve.peakStart,
        curve.peakEnd,
        v -> {
          curve.peakStart = v;
          curve.invalidateAcquisition();
          show(0);
        },
        v -> {
          curve.peakEnd = v;
          curve.invalidateAcquisition();
          show(0);
        });
    view.text("设备端仅支持英文输入；中文名称由宿主统一维护。", true);
    single(true, "下一步：活化参数", () -> show(1));
  }

  private void activation() {
    parameter("最高电位 (mV)", curve.endPotential, v -> curve.endPotential = v, true);
    parameter("步进 (mV)", curve.step, v -> curve.step = v, true);
    parameter("速率 (mV/s)", curve.rate, v -> curve.rate = v, true);
    parameter("圈数 (次)", curve.cycles, v -> curve.cycles = v, true);
    parameter("静置 (s)", curve.quiet, v -> curve.quiet = v, true);
    parameter("沉积 (s)", curve.hold, v -> curve.hold = v, true);
    parameter("起始电位 (mV)", curve.startPotential, v -> curve.startPotential = v, true);
    view.text("自动活化将按以上参数通过设备适配器执行，并记录活化曲线。", true);
    buttons(
        true,
        "自动活化",
        true,
        () -> {
          curve.validateActivation();
          view.progress(
              "自动活化",
              "正在执行电极活化扫描…",
              1,
              i -> {
                curve.activation = workstation.activation(curve).toMillivolts();
                curve.activationUnitVerified = true;
                curve.origin =
                    curve.baseline == null
                        ? workstation.origin()
                        : DataOrigin.combine(curve.origin, workstation.origin());
              },
              () -> {
                show(1);
                view.message("自动活化采集完成");
              });
        },
        "下一步：曲线",
        false,
        () -> show(2));
  }

  private void points() {
    if (curve.baseline == null) view.text("⚠ 请先采集基线，再添加不同浓度点。", true);
    else
      view.baseline(
          "✓ 基线已就绪 — 峰电流 " + StandardCurve.number(curve.baselinePeak()) + " μA",
          () -> view.confirm("重新采集基线", "重新采集会清除浓度点及 LOD 结果，是否继续？", this::baseline));
    view.field(
        "浓度",
        StandardCurve.number(concentration) + "   pg/mL",
        false,
        s -> {
          double d = StandardCurve.parseNumber(s.replace("pg/mL", "").trim());
          StandardCurve.finite(d);
          if (d <= 0) throw new IllegalArgumentException("浓度必须大于 0");
          concentration = d;
          show(2);
        });
    view.text("浓度                 单位          点数          操作", true);
    for (int i = 0; i < curve.points.size(); i++) {
      StandardCurve.Point point = curve.points.get(i);
      final int index = i;
      view.buttons(
          new String[] {
            StandardCurve.number(point.concentration)
                + "   pg/mL    "
                + point.signal.voltages().length,
            "删除"
          },
          new boolean[] {false, false},
          new Runnable[] {
            () -> {
              concentration = point.concentration;
              show(2);
            },
            () -> {
              curve.removePoint(index);
              show(2);
            }
          },
          false);
    }
    if (curve.baseline == null) single(true, "采集基线", this::baseline);
    else {
      buttons(
          true,
          "添加浓度",
          false,
          () -> {
            if (curve.points.stream().anyMatch(p -> p.concentration == concentration))
              throw new IllegalArgumentException("该浓度已存在，请输入不同浓度");
            if (curve.points.size() >= 10) throw new IllegalArgumentException("最多添加 10 个浓度点");
            Signal[] signals = new Signal[3];
            view.progress(
                "浓度采集",
                "采集 " + concentration + " pg/mL 数据中…",
                3,
                i ->
                    signals[i] =
                        workstation.sampleStandard(concentration, curve, i, 3).toMillivolts(),
                () -> {
                  curve.addPoint(concentration, signals);
                  curve.origin = DataOrigin.combine(curve.origin, workstation.origin());
                  show(2);
                  view.message("浓度采集完成：3 次重复");
                });
          },
          "下一步：LOD/LOQ",
          true,
          () -> show(3));
    }
  }

  private void baseline() {
    Signal[] signals = new Signal[3];
    view.progress(
        "基线采集",
        "采集基线数据中…",
        3,
        i -> signals[i] = workstation.baseline(curve, i).toMillivolts(),
        () -> {
          double peak =
              Arrays.stream(signals)
                  .mapToDouble(s -> s.peakWithin(curve.peakStart, curve.peakEnd))
                  .average()
                  .orElseThrow(IllegalStateException::new);
          view.confirm(
              "基线采集完成",
              "基线峰电流\n"
                  + StandardCurve.number(peak)
                  + " μA\n每条采集点数："
                  + signals[0].voltages().length
                  + " 点 | 重复：3 次",
              () -> {
                curve.confirmBaseline(signals);
                curve.origin = DataOrigin.combine(curve.origin, workstation.origin());
                show(2);
                view.message("基线采集已确认：峰电流 " + StandardCurve.number(curve.baselinePeak()) + " μA");
              });
        });
  }

  private void lod() {
    view.choice(
        "LOD 确定方式",
        new String[] {"手动录入", "自动计算"},
        curve.automatic ? 1 : 0,
        i -> {
          curve.invalidateLod();
          curve.automatic = i == 1;
          show(3);
        });
    if (curve.automatic) {
      view.text(
          "低浓度点：固定曲线中最低浓度 " + curve.points.get(0).concentration + " pg/mL，随后独立采集 10 条。", true);
      view.text("自动计算\n\n" + curve.statistics(), false);
    } else parameter("LOD（检出限）pg/mL", curve.lod, curve::manualLod, false);
    view.text("LOQ（定量限）    " + StandardCurve.number(curve.loq) + " pg/mL", false);
    view.text("手动录入 LOD 后，LOQ = 4.0 × LOD 自动得出；或切换到「自动计算」，由低浓度重复采集求出。", true);
    if (curve.automatic)
      buttons(
          false,
          "采集 10 条并计算",
          false,
          () -> {
            Calibration fit = curve.fit();
            double[] cs = new double[10];
            double low = curve.points.get(0).concentration;
            Signal[] signals = new Signal[10];
            view.progress(
                "自动 LOD 采集",
                "采集低浓度数据中…",
                10,
                i -> {
                  signals[i] = workstation.sampleStandard(low, curve, i, 10).toMillivolts();
                  cs[i] =
                      fit.concentration(
                          signals[i].peakWithin(curve.peakStart, curve.peakEnd)
                              - curve.baselinePeak());
                },
                () -> {
                  curve.calculateLod(cs);
                  curve.lodSignals = signals;
                  curve.origin = DataOrigin.combine(curve.origin, workstation.origin());
                  show(3);
                  view.message("自动 LOD 完成：10 条，Sc=" + StandardCurve.number(curve.sd) + " pg/mL");
                });
          },
          "查看详细计算过程",
          false,
          () -> view.details("LOD / LOQ 详细计算过程", curve.lodDetails()));
    else
      single(
          false,
          "查看详细计算过程",
          () ->
              view.details(
                  "LOD / LOQ 详细计算过程", "手动录入 LOD\nLOQ = 4.0 × LOD\n\n" + curve.statistics()));
    single(true, "下一步：导出与保存", () -> show(4));
  }

  private void exports() {
    buttons(
        false,
        "查看曲线",
        false,
        () -> viewer(0),
        "结果保存",
        true,
        () -> {
          try {
            library.save(curve);
            saved.run();
            status = "标准曲线已保存，可在样品测试中选择";
            show(4);
            view.message(status);
          } catch (java.io.IOException e) {
            throw new IllegalStateException("保存失败：" + e.getMessage(), e);
          }
        });
    buttons(
        false,
        "导出本地",
        false,
        () -> view.export(curve, false),
        "导出 U 盘",
        false,
        () -> view.export(curve, true));
    view.text("目的地\n\n" + view.destination(), true);
    view.buttons(new String[] {"重试"}, new boolean[] {false}, new Runnable[] {() -> show(4)}, false);
    view.text("JSON 与 Excel 可导出；「结果保存」会写入本地标曲库，保存后该标曲可在样品测试中选择。", true);
    view.text("状态   " + status, true);
  }

  /** 曲线查看器与截图一致，包含活化、曲线、方程和 LOD 四类数据。 */
  public void viewer(int index) {
    String[] tabs = {"活化", "曲线", "方程", "LOD/LOQ"};
    view.page("曲线查看器", curve.origin.label + " · " + curve.name, tabs, index, this::viewer);
    if (index == 0) {
      if (curve.activation == null) view.text("尚未执行自动活化", true);
      else if (curve.activationUnitVerified) view.plot(curve.activation, "mV", "活化曲线");
      else view.text("旧版活化记录的电位单位未核实，请重新活化后查看。原始值仍保留。", true);
    } else if (index == 1) {
      if (curve.baseline != null)
        view.plot(
            curve.baselineSignals.length == 3
                ? Signal.average(curve.baselineSignals)
                : curve.trace(curve.baselinePeak()),
            "mV",
            curve.baselineSignals.length == 3 ? "基线（三次平均）" : "旧版基线（峰值示意）");
      for (StandardCurve.Point p : curve.points)
        view.plot(
            p.signal,
            "mV",
            p.concentration + " pg/mL" + (p.repeats.length == 0 ? "（旧版峰值示意）" : "（三次平均）"));
    } else if (index == 2) {
      Calibration fit = curve.fit();
      view.text(fit.equation(), false);
      view.plot(fit.fittedLine(), "pg/mL", "标准曲线");
    } else view.text(curve.lodDetails(), false);
    single(true, "返回编辑", () -> show(tab));
  }
}
