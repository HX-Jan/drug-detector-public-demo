package org.example.detector.android;

import static org.junit.Assert.*;

import android.app.Dialog;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

/**
 * Native Android view rendering under Robolectric, not device or hardware
 * validation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReferenceUiTest {
  private MainActivity activity;

  private View find(View parent, String text) {
    if (parent instanceof TextView && text.contentEquals(((TextView) parent).getText()))
      return parent;
    if (parent instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) parent).getChildCount(); i++) {
        View found = find(((ViewGroup) parent).getChildAt(i), text);
        if (found != null)
          return found;
      }
    return null;
  }

  private View root() {
    return activity.getWindow().getDecorView();
  }

  private View tag(View parent, String tag) {
    if (tag.equals(parent.getTag()))
      return parent;
    if (parent instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) parent).getChildCount(); i++) {
        View found = tag(((ViewGroup) parent).getChildAt(i), tag);
        if (found != null)
          return found;
      }
    return null;
  }
  /** 实际点击参数并通过界面键盘提交，避免绕过向导校验。 */
  private void parameter(String label, String value) throws IOException {
    View field = tag(root(), label);
    assertNotNull(label, field);
    field.performClick();
    Dialog dialog = ShadowDialog.getLatestDialog();
    TextView input = (TextView) tag(dialog.getWindow().getDecorView(), "keypad-input");
    assertNotNull(input);
    input.setText(value);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    if (label.equals("英文名") || label.equals("浓度"))
      render(label.equals("英文名") ? "android-english-keypad" : "android-numeric-keypad", 360, 640);
    click("确认");
  }

  private void click(String text) {
    View v = find(root(), text);
    Dialog dialog = ShadowDialog.getLatestDialog();
    if (dialog != null && dialog.isShowing()) {
      View inDialog = find(dialog.getWindow().getDecorView(), text);
      if (inDialog != null)
        v = inDialog;
    }
    assertNotNull("Missing control: " + text, v);
    assertTrue("Disabled control: " + text, v.isEnabled());
    if (!v.isClickable() && v.getParent() instanceof View)
      v = (View) v.getParent();
    assertTrue(v.performClick());
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  private void advance(long millis) {
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
  }

  private void render(String name, int width, int height) throws IOException {
    View root = root();
    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, width, height);
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.WHITE);
    root.draw(canvas);
    Dialog dialog = ShadowDialog.getLatestDialog();
    if (dialog != null && dialog.isShowing()) {
      canvas.drawColor(Color.argb(95, 22, 40, 66));
      View decor = dialog.getWindow().getDecorView();
      int w = Math.min(width - 50, 340);
      decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height - 50, View.MeasureSpec.AT_MOST));
      int h = decor.getMeasuredHeight();
      decor.layout(0, 0, w, h);
      canvas.save();
      canvas.translate((width - w) / 2f, (height - h) / 2f);
      decor.draw(canvas);
      canvas.restore();
    }
    File dir = new File(System.getProperty("detector.render.dir", "build/ui-evidence"));
    if (!dir.isDirectory() && !dir.mkdirs())
      throw new IOException("Cannot create render directory");
    try (OutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
    }
    bitmap.recycle();
  }

  @Test
  public void referenceFlowAndNativeViewRenders() throws Exception {
    try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)
        .setup().visible()) {
      activity = controller.get();
      render("android-home", 360, 640);
      assertTrue(activity.workstation.connected());
      render("android-connected", 360, 640);
      click("开始样品测试");
      render("android-workflow", 360, 640);
      click("开始活化");
      advance(200);
      render("android-activation", 360, 640);
      advance(1500);
      click("已加样，开始反应计时");
      assertFalse(find(root(), "计时结束后继续").isEnabled());
      assertNotNull(find(root(), "跳过计时（展示模式）"));
      render("android-reaction", 360, 640);
      click("跳过计时（展示模式）");
      click("开始检测");
      advance(200);
      render("android-measurement", 360, 640);
      advance(1700);
      assertNotNull(find(root(), "模拟检测完成"));
      assertEquals(1, new SqliteResultRepository(activity).all().size());
      render("android-result", 360, 640);
      click("DPV 曲线");
      render("android-signal", 360, 640);
      click("历史记录");
      render("android-history", 360, 640);
      click("‹");
      click("系统设置");
      click("开启管理员模式");
      render("android-authorization", 360, 640);
      click("0");
      click("确认");
      assertTrue(ShadowDialog.getLatestDialog().isShowing());
      for (String key : new String[]{"1", "2", "3", "4", "5", "6"})
        click(key);
      click("确认");
      click("标准曲线管理");
      int curvesBeforeDelete = activity.library.all().size();
      render("android-curve-library", 360, 640);
      RadioGroup group = null;
      for (int i = 0; i < activity.body.getChildCount(); i++)
        if (activity.body.getChildAt(i) instanceof RadioGroup)
          group = (RadioGroup) activity.body.getChildAt(i);
      assertNotNull(group);
      group.getChildAt(0).performClick();
      click("删除");
      render("android-curve-delete-confirm", 360, 640);
      click("取消");
      assertEquals(curvesBeforeDelete, activity.library.all().size());
      click("删除");
      click("确认删除");
      assertEquals(curvesBeforeDelete - 1, activity.library.all().size());
      click("添加");
      render("android-curve-info", 360, 640);
      parameter("英文名", "GH");
      parameter("扫描范围起点 (mV)", "-500");
      parameter("扫描范围终点 (mV)", "500");
      parameter("寻峰范围起点 (mV)", "-200");
      parameter("寻峰范围终点 (mV)", "200");
      click("下一步：活化参数");
      render("android-curve-activation", 360, 640);
      click("自动活化");
      advance(100);
      render("android-curve-activation-progress", 360, 640);
      advance(500);
      click("下一步：曲线");
      click("采集基线");
      advance(1500);
      render("android-curve-baseline-confirm", 360, 640);
      click("确认");
      click("添加浓度");
      advance(1500);
      parameter("浓度", "10");
      click("添加浓度");
      advance(1500);
      render("android-curve-points", 360, 640);
      click("下一步：LOD/LOQ");
      Spinner method = null;
      for (int i = 0; i < activity.body.getChildCount(); i++)
        if (activity.body.getChildAt(i) instanceof Spinner)
          method = (Spinner) activity.body.getChildAt(i);
      assertNotNull(method);
      method.setSelection(1);
      advance(10);
      render("android-curve-lod", 360, 640);
      click("采集 10 条并计算");
      advance(5000);
      render("android-curve-lod-result", 360, 640);
      click("查看详细计算过程");
      render("android-curve-lod-details", 360, 640);
      click("关闭");
      click("下一步：导出与保存");
      click("结果保存");
      click("导出本地");
      render("android-curve-export", 360, 640);
      assertTrue(activity.library.all().stream().anyMatch(c -> c.name.equals("GH")));
      click("查看曲线");
      render("android-curve-viewer", 360, 640);
      click("方程");
      click("LOD/LOQ");
      click("返回编辑");
      render("android-calibration", 360, 640);
      render("android-compact", 320, 568);
      // Rotation/recreation must preserve the saved record and reset the unfinished
      // UI state.
      controller.recreate();
      activity = controller.get();
      assertNotNull(find(root(), "毒品快速检测仪"));
      try (SqliteResultRepository repo = new SqliteResultRepository(activity)) {
        assertEquals(1, repo.all().size());
      }
    }
  }
}
