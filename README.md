# 便携式毒品直读检测仪 公开演示版

基于功能说明与演示视频独立编写的 Java 功能原型。用于展示检测流程、数学计算和数据管理，不包含原产品源代码、私有通信协议或真实检测数据。**本仓库是公开演示版，不能表述为原设备的完整源码或已通过真实样品验证的检测系统。**

## 基础框架

| 模块 | 技术 | 职责 |
| --- | --- | --- |
| `app` | Android 原生 Java、系统 View、SQLite | 触屏应用、流程引导、记录查询、系统文件选择器导出 |
| `core` | Java 17，无第三方运行依赖 | 状态机、标曲拟合、合成信号、浓度换算、记录编码、XLSX 导出 |
| `desktop` | Java Swing | Windows / macOS / Linux 电脑演示，共用核心逻辑 |

Android 最低版本为 8.0（API 26），编译目标 API 35。构建固定使用 AGP 8.9.2、Gradle 8.11.1、JDK 17；版本兼容关系见 [Android 官方说明](https://developer.android.com/build/releases/agp-8-9-0-release-notes)。

## v0.2 界面复现

以设备演示视频为主要视觉依据，补充文档中的历史记录和曲线详情。Android 和电脑入口采用一致的竖屏白底、蓝色按钮、三项快捷入口、五段检测步骤条、居中进度弹窗和数字键盘。参照位置及保留差异见 [界面对照说明](docs/UI_REFERENCE.md)。

下图是 Android 原生 View 在 Robolectric 中的实际测试渲染，非真机截图，数据均为合成：

| 首页 | 检测流程 | 管理员弹窗 |
| --- | --- | --- |
| <img src="docs/screenshots/android-connected.png" width="210" alt="Android 首页"/> | <img src="docs/screenshots/android-workflow.png" width="210" alt="Android 检测流程"/> | <img src="docs/screenshots/android-authorization.png" width="210" alt="Android 管理员演示弹窗"/> |

## 基础功能

- 模拟工作站连接与断开，未连接时禁止检测。
- 选择甲基苯丙胺、吗啡、曲马多、美沙酮及虚构样品编号。
- 确认电极并模拟活化 → 加样 → 等待反应 → 冲洗与添加反应液 → 模拟采集 → 结果保存。
- 5/10 分钟常规计时，以及明确标注的 5 秒演示加速模式。
- 生成合成 DPV 曲线，计算正向峰值及峰电流差，按标曲反算浓度。
- 5–10 个标定点的一元线性拟合，计算斜率、截距与 Pearson r；拒绝重复浓度、非有限值、零方差和非正斜率。
- 本地记录、样品编号与类型组合查询、详情、单条/多条 Excel 导出。Excel 同时保存汇总和采样点，所有行带 `SIMULATED` 标记。
- Android 保存标曲、计时设置和记录；电脑演示持久保存记录，标曲和设置仅在本次会话有效。

## 快速运行电脑演示

安装 JDK 17 或更高版本。在仓库根目录打开 PowerShell：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\desktop.ps1
```

该入口无需 Android SDK 和 Gradle 下载，先编译、执行核心验收，再打开演示界面。JDK 的 `java` 和 `javac` 需在 PATH 中。

1. 点击“连接设备”→“连接模拟设备”。
2. 点击“开始样品测试”，使用自动生成的 `DEMO-…` 编号。
3. 点击“开始测试”，逐步进行模拟活化、加样、反应和采集。默认反应计时为演示加速 5 秒。
4. 查看合成结果、历史记录，选择一条或多条记录导出 `.xlsx`。

电脑记录写入启动目录下的 `data/`，默认不进入 Git。请仅录入虚构编号。

标曲入口为“系统设置”→“标准曲线管理”，演示口令 **123456**。这是为了复现视频中的数字键盘交互，不提供真实厂家权限控制。

电脑演示入口的实际结果页面（合成数据）：

![电脑演示结果](docs/screenshots/desktop-result.png)

## 构建 Android

使用 Android Studio 打开本仓库，设置 Gradle JDK 为 17，安装 Android SDK Platform 35 和 Build Tools 35.0.0。SDK 路径由 Android Studio 写入本地 `local.properties`，此文件不提交。

```powershell
.\gradlew.bat :core:check :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

产物为 `app/build/outputs/apk/debug/app-debug.apk`，是调试签名的演示 APK。安装后先连接模拟设备。Android 导出使用系统“另存为”界面，可选择本地或系统已挂载的 U 盘，无需申请所有文件存储权限。取消文件选择不会导出。

仅验证电脑模块时可跳过 Android 插件与 SDK：

```powershell
.\gradlew.bat -PdesktopOnly :core:check :desktop:build
.\gradlew.bat -PdesktopOnly :desktop:run
```

Android 页面重建会取消未完成的演示流程，已写入 SQLite 的结果保留。导出选择若因进程重建丢失，请重新选择记录导出；初版不支持恢复未完成采集。

## 数据与算法边界

单位统一为 **ng/L**（浓度）、**μA**（电流）、**V**（电位）。采用一体机说明中的相邻电流差：`y_i = I_i − I_(i−1)`，拟合 `y = kC + b`，反算 `C = (ΔI − b) / k`。未知样品的差值以本次基线峰与样品峰之差演示。

默认标曲是人为构造的数学样例，`r=1` 来自完全线性合成点；不代表仪器精度。模拟器根据样品编号确定性地生成高斯曲线，所选检测类型是操作输入，不是算法识别结果。更换标曲不会改变模拟器的固定合成模型，结果可能超出区间；页面会标注，且不作阳性/阴性判断。

真实 USB/BLE、DPV/SWV 信号预处理、负向峰、基线漂移校正、扫码、厂家权限与真实样品性能验证均未实现。未提供标准/法规阈值，因此没有超标判定。原材料中的 LOD 演示不在本次基础版本中实现。

详见 [功能范围](docs/SCOPE.md)、[架构与接入](docs/ARCHITECTURE.md)、[验收说明](docs/VALIDATION.md) 和 [公开版本说明](docs/PUBLIC_DISCLOSURE.md)。
