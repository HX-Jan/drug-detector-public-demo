# v1.0.1 修复与验收

2026-10-17，基于更新后的主分支 `89d82ce` 修复。继续使用 Java 17、原生 Android 与 Swing，共用原有白底蓝色、五步向导、弹窗及触屏键盘设计。

## 修复内容

| 问题 | 本次行为 |
| --- | --- |
| 采集次数只对应动画 | 每次进度完成调用一次适配器；基线 3 次、每个浓度 3 次、自动 LOD 另采 10 次；完整记录逐次轨迹，平均曲线由原始序列平均产生 |
| 来源标识缺失 | 页面保留“演示模式 · 合成信号”，结果显示“模拟检测完成”；记录编码、标曲库、JSON、Excel 保留 SIMULATED，旧记录缺失来源为 UNKNOWN |
| 默认标曲删除后恢复或启动冲突 | 只在库文件尚不存在时一次性初始化，删除、清空以及同名自建标曲均保留状态 |
| 检测类型无法选择 | 首次启动提供四种模板，新建标曲增加四种类型选择，类型随保存重启保留 |
| 活化电位/采样单位错误 | 专用活化接口接收参数；适配器返回 V，入标曲库显式转为 mV；采样点数取实际信号，保存并导出逐次采样。旧活化原值不猜测转换，单位 UNKNOWN 并提示重新采集 |

自动 LOD 的统计公式保持 `Sc = sqrt(sum((Ci−mean)^2)/9)`、`LOD = 2.82×Sc`、`LOQ = 4×LOD`。新向导保持固定基线差、至少两个浓度点的现有规则；旧相邻差值接口继续兼容。确定性模拟偏差仅用于演示，不是仪器性能指标。

## 可重复检查

```powershell
.\gradlew.bat :core:check :desktop:build :desktop:verifyUi :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon
```

没有 Android SDK 时可运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\desktop.ps1 -JdkHome 'C:\Program Files\Java\jdk-17' -VerifyUi
```

`AcquisitionVerification` 注入独立计数设备，以平方序列生成十次偏差并独立重算 LOD，避免用模拟器自身常数验证自身。还检查模板删除/清空后重启、四种类型、采样参数、V/mV、逐次轨迹读回、来源导出和旧二进制记录兼容。Android 与电脑流程进一步检查实际界面选择美沙酮、保存后的三次/十次轨迹及模拟来源。

## 实际结果

本机 Windows、JDK 17、Gradle 8.11.1 在中文路径直接执行，完整构建通过；本次不使用英文路径副本。

| 检查 | 实际结果 |
| --- | --- |
| 原核心检查 | 42 项通过，含流程、数学、保存和检测 Excel 来源 |
| 新采集回归 | 36 项通过，含独立计数设备、平方偏差 LOD、删除/清空重启、原始轨迹、单位、四类与兼容读取 |
| 标曲/模板检查 | 拟合、LOD/LOQ、非法输入、失效、保存/删除重启、JSON/XLSX、内置模拟设备通过 |
| 电脑实际界面 | 完整流程通过，选择美沙酮后保存并检查三次/十次轨迹；实际导出；27 张窗口渲染 |
| Android 原生界面 | Robolectric NATIVE API 35，1 个完整流程用例，0 failures / 0 errors；检查美沙酮选择与逐次轨迹；27 张渲染，含 360×640 与 320×568 |
| APK / Lint | `assembleDebug` 通过，v1.0.1 / versionCode 4；Lint 0 errors，10 warnings，仍有国际化、无障碍和工具版本等非阻断项 |
| 独立文件读取 | Python JSON 与 openpyxl 读取计数设备夹具；2 张表，采集表 86 行，来源/逐次曲线/单位/平均轨迹正确，无公式单元格；独立反算与样本标准差重算 LOD=0.997747290776、LOQ=3.990989163102 一致。此数值属于测试夹具 |
| 无 SDK 入口 | `scripts/desktop.ps1 -VerifyOnly -JdkHome ...` 编译及全部核心检查通过 |
| 空白检查 | `git diff --check` 通过 |

截图目录 [screenshots/v1.0.1/](screenshots/v1.0.1/) 收录 19 张当前运行证据，其余渲染位于两端 `build/ui-evidence/`。Android 图并非真机截图。GitHub 远程运行状态以 [Actions](https://github.com/HX-Jan/drug-detector-public-demo/actions) 为准，发布说明链接到对应运行。

## 保留边界

没有执行 Android 真机、系统文件选择器、实际 U 盘或真实设备验证。没有实现真实 USB/BLE、真实厂家授权或真实样品性能测试。同步适配器仍只适用于立即返回的合成数据，真实 I/O 需后台、超时、取消和断连处理。

上游更新使用 Android 包名 `org.example.detector.app`；v1.0.1 沿用它。更早 `org.example.detector.android` 的旧应用属于另一包，未提供跨包历史迁移；不会删除旧应用数据。旧标曲/记录兼容读取，不回填无法证实的来源或电位单位。
