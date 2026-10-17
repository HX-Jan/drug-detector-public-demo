# 正式界面实施计划

> **For agentic workers:** Use executing-plans to implement task-by-task. Delegate the native UI adaptation as one bounded task; core and UI use the contracts below. Review both requirements and quality before completion.

**Goal:** 修改现有 Java 两端，采用真实数据边界、可替换通信接口、完整状态与本地持久化，未接入时禁止采集和判定。

**Architecture:** core 提供设备、授权、检测状态、采集和仓储服务；桌面 Swing 与 Android View 只适配平台 UI。两端默认实例化未配置设备适配器。

**Tech Stack:** Java 17、Swing、Android API 26+、SQLite、原子文件写入。

## Global Constraints

- 在现有 Java Swing 桌面端、原生 Android 和共享 core 上修改。
- 未接入时保持未连接，禁止采集和生成判定。
- 禁止通过本地生成信号补足设备响应。
- 不回滚、不覆盖无关变更；禁止直接删除用户记录。
- 界面不出现用户禁止的文案和调试入口。
- 目标桌面分辨率 1440×900，保留居中竖屏信息结构。

## Task 1 共享设备与数据边界

Files: `core/src/main/java/org/example/detector/core/{Workstation,UnavailableWorkstation,DeviceDescriptor,WorkstationProvider,CurveLibrary,StandardCurve,CurveWorkflow,Signal,Calibration}.java`；`core/src/test/java/org/example/detector/core/ProductionVerification.java`。

- [ ] 首先新增行为验证：新建库必须为空。运行并观察现有代码因预置曲线导致断言失败。
- [ ] 将工作站接口扩展为 discover、connect(DeviceDescriptor)、activate(StandardCurve)、scan(StandardCurve)、cancel、deviceName。默认适配器抛出“设备通信尚未配置”，connected 永远为 false。
- [ ] 正式运行源不包含信号生成器；原有生成器及数据构造助手迁往验证代码，验证改用局部数据构造。
- [ ] 标曲持久化保存原始采样曲线，而非从峰值重新构造。损坏数据拒绝读取；新目录不生成预置记录。
- [ ] CurveWorkflow 注入 Workstation，通过 View 的后台任务方法采集；不再用定时器构造成功状态。
- [ ] 回归：空库、拒绝未连接采集、拟合与 LOD、采样往返、损坏记录与导出内容。

## Task 2 共享授权与流程

Files: `core/src/main/java/org/example/detector/core/{AdminAccess,AuditLog,DetectionSession,DetectionResult,ResultCodec,FileResultRepository,XlsxExporter,CurveExport}.java`。

- [ ] 增加凭据首次设置、错误密码、持久锁定和过期会话验证；观察缺失行为失败。
- [ ] AdminAccess(Path) 提供 configured/setup(char[])/login(char[])/logout/authorized/requireAdministrator；六位凭据用 PBKDF2 加随机盐存储。
- [ ] DetectionSession 增加明确冲洗、反应液、失败、待保存与跳过计时审计状态；样本、试剂批号、标曲版本快照随运行记录保存。
- [ ] DetectionResult.status() 默认返回待判定或超出定量范围，缺少政策不生成阴阳性结论。
- [ ] 导出正式业务表头，原始数据仍数值写入并防止公式注入。保存以 ID 幂等；保存失败不进入成功状态。
- [ ] 回归：非法跳步、取消后拒绝完成、跳过标记、缺少批号、重复保存、编码重启与授权校验。

## Task 3 两端原生界面适配（委派）

Files: `desktop/src/main/java/org/example/detector/desktop/*.java`；`app/src/main/java/org/example/detector/android/*.java`；Android manifest 和模块构建配置；两端 UI 验证。

Consumes: `WorkstationProvider.open()`、`Workstation.discover():List<DeviceDescriptor>`、`connect(DeviceDescriptor)`、`deviceName():String`、`activate(StandardCurve):Signal`、`scan(StandardCurve):Signal`；`AdminAccess(Path)` 及 Task 2 方法；新增接口传递失败时使用原有 core 状态方法的兼容入口，控制器仅根据实际成功推进。

- [ ] 新增默认未连接、空标曲、无固定口令和无禁用文案验证，先运行观察失败。
- [ ] 修改两端文案、应用标题、图标、空态与错误态；正式数据使用新版本目录/数据库，旧记录不删除、不自动导入。
- [ ] UI 将扫描、连接和采集放后台执行，显示实际错误和可重试入口；不得用固定延迟表示成功。
- [ ] 授权弹窗支持首次设置，管理操作要求有效会话；退出会话后禁止修改标曲。
- [ ] 桌面 1440×900，内容居中，正文滚动，底部固定；Android 窄屏保留触屏键盘与五步结构。
- [ ] 编译两端并获取实际渲染图，检查布局与交互。

## Task 4 整体验证与交付

Files: `scripts/desktop.ps1`、`core/build.gradle`、`README.md`、`docs/VALIDATION.md`、`docs/PRODUCTION_INTERFACE.md`。

- [ ] 更新脚本运行正式验证；依次运行共享核心与桌面 UI 验证。
- [ ] 使用缓存 JDK17、Android SDK 执行 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`；如环境阻碍，保存准确失败证据，不声称通过。
- [ ] 扫描可见文案、调试入口、固定口令、运行源信号生成代码；检查 git diff 不包含无关回退。
- [ ] 委派最终代码审查，修复重要问题，重跑受影响验证。
- [ ] 文档记录运行命令、通信替换方法、模型、截图差异与实际验证范围；不提交用户已有的无关变更。
