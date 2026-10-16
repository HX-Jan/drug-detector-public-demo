# 初版验收记录

验证日期：2026-10-16，Windows，JDK 17 构建，Java 17 语言级别。

## 已执行

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| 核心验收 | 40 项通过 | `CoreVerification` 可独立执行，覆盖回归拟合、反算、非有限值、无效浓度、零方差、负斜率等 |
| 流程约束 | 通过 | 未连接拒绝采集，提前完成反应被拒绝，取消后不可继续，重复完成被拒绝 |
| 记录读写 | 通过 | 编码解码保留标曲与 101 点信号；重新打开存储可查到结果；重复 ID 拒绝覆盖 |
| 电脑界面闭环 | 通过 | 实际驱动本应用 Swing 组件完成连接、登记、计时、检测、保存、历史查询、标曲计算和重启 |
| 页面检查 | 通过 | 检查了电脑首页、结果、历史与标曲四张实际渲染图，示例见 screenshots 目录 |
| Excel 独立读取 | 通过 | 使用 openpyxl 读取真实导出文件，两张表、1 条记录和 101 个采样点，浓度与独立重算一致；无公式单元格 |
| Android 源码编译 | 通过 | Android API 35 编译类路径验证 |
| Android APK 构建 | 通过 | `:app:assembleDebug`，产物为调试签名演示 APK |
| Android Lint | 通过 | `:app:lintDebug` 无阻断错误；仍有工具版本、绘图分配和国际化等警告 |

## 可重复执行

无需 Gradle 或 Android SDK 的核心验收：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\desktop.ps1 -VerifyOnly
```

完整构建：

```powershell
.\gradlew.bat :core:check :desktop:build :app:assembleDebug :app:lintDebug
```

电脑界面自动验证（需图形桌面，测试创建并关闭自己的演示窗口，不操作其他应用）：

```powershell
.\gradlew.bat -PdesktopOnly :desktop:verifyUi
```

测试使用临时合成记录，不加载开发者真实数据。GitHub Actions 配置在每次推送运行同样的核心、构建与电脑界面检查，远程执行状态以仓库 Actions 页面为准。

## 尚未验证

未执行 Android 真机或模拟器端到端操作，也未执行 Android SQLite、文件选择器及旋转重建的仪器测试。没有验证任何真实 USB/BLE 设备、真实样品、检测性能、U 盘兼容性、厂家权限或真实电池协议。电脑闭环通过与 APK 编译成功不能替代上述验证。

程序拟合、计算和文件读写的通过，仅证明合成数据场景下的软件基础功能。
