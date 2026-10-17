# v1.1.0 AI 接口验收

2026-10-17，基于 v1.0.1 增加公开 Java AI 接口与本地模拟适配器。保留原有界面和五项采集修复，当前 AI 功能边界见 [接入说明](AI_INTEGRATION.md)。

## 已执行检查

- 核心原有 42 项、采集回归 36 项、标曲和模板检查继续通过。
- 新增 `AiVerification` 32 项：独立已知波形特征、边缘峰/无净峰、空置信度、接口替换、请求串单拒绝、服务失败不兜底、输入校验、AI 快照编码/保存重启、v1/v2 兼容、Excel、JSON 转义和可运行示例入口。
- Android Robolectric NATIVE API 35 完整流程通过，0 failures / 0 errors；31 张渲染，新增 AI 进度、AI 结果、滚动模型信息及窄屏页面，SQLite 保存和重建后 AI 快照仍在。
- Swing 真实窗口完成新检测，显示 AI 分析页，重启后分析结果仍在；28 张窗口渲染。
- APK 构建通过，v1.1.0 / versionCode 5；Lint 0 errors、10 warnings。

- `:core:demoAi -PaiOutput=core/build/ai-demo` 实际生成请求/响应 JSON 和检测 Excel；当前文件已收录在 [ai-example](ai-example/) 中。
- Python 独立读取 JSON、重算峰位/峰高/净峰/首尾均值差和标曲浓度；openpyxl 读取 2 张工作表、28 个汇总字段和 101 个采样点，模型/请求 ID/来源一致，JSON 分类/置信度为空值，Excel 对应为空白，无公式单元格。
- 无 Android SDK 的 `scripts/desktop.ps1 -VerifyOnly -JdkHome ...` 编译及全部核心检查通过；APK 版本和签名校验通过；`git diff --check` 通过。

本机完整任务在 Windows 中文路径执行，使用 JDK 17、Gradle 8.11.1。当前收录 9 张截图于 [screenshots/v1.1.0](screenshots/v1.1.0/)。GitHub 远程执行状态以 [Actions](https://github.com/HX-Jan/drug-detector-public-demo/actions) 为准，发布说明链接到对应运行。

## 可复现

```powershell
.\gradlew.bat :core:check :desktop:build :desktop:verifyUi :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon
.\gradlew.bat -PdesktopOnly :core:demoAi -PaiOutput=core/build/ai-demo
```

截图来自 Swing 和 Robolectric 原生 View，Android 图不是真机截图。以上检查证明软件接口及模拟流程可运行，未验证真实 AI 模型、分类准确率、网络互通、真机、真实仪器或样品。
