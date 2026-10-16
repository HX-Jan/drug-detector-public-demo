# 第三方工具说明

Java 源码未引入第三方运行库。Android 应用使用 Android 系统 API；电脑入口使用 JDK 标准库。

Android 测试使用 JUnit 4.13.2 和 Robolectric 4.14.1，仅存在于测试依赖中，不打入 APK。Robolectric 用于在 JVM 中运行 Android 原生 View、SQLite 和生命周期测试，并生成原生渲染图，相关用法见 [官方测试文档](https://robolectric.org/getting-started/)。这些渲染不替代设备实测。

Gradle Wrapper 脚本和 JAR 由 Gradle 8.11.1 生成，来源为 https://github.com/gradle/gradle 。Gradle 上游采用 Apache License 2.0，见 https://github.com/gradle/gradle/blob/v8.11.1/LICENSE 。Wrapper 下载官方 Gradle 分发包并核对 SHA-256；分发包和 Android SDK 不包含在本仓库。

Android Gradle Plugin 8.9.2 由构建时的 Google Maven 仓库解析。SDK 的许可由使用者在本地开发环境管理。团队新写源码的许可状态见 docs/PUBLIC_DISCLOSURE.md。
