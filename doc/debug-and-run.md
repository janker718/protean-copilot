# IDEA 插件调试运行说明

本文说明如何在本地运行、调试和打包 `Protean Copilot` IntelliJ IDEA 插件。

## 环境要求

- macOS / Windows / Linux 均可。
- IntelliJ IDEA 建议使用较新的版本。
- Gradle 使用项目自带的 Wrapper：`./gradlew`。
- 当前项目按 JetBrains 官方插件模板配置为 Java 21 编译目标。
- 如果本机没有 JDK 21，Gradle 会通过 `org.gradle.toolchains.foojay-resolver-convention` 自动下载合适的 JDK 21。

本机只要有可运行 Gradle 的 JDK 17+ 即可启动构建。当前机器上已验证 JDK 24 可启动 Gradle，构建时自动补齐 JDK 21 toolchain。

## 用 IDEA 打开项目

1. 打开 IntelliJ IDEA。
2. 选择 `Open`。
3. 打开项目目录：

```text
/Users/janker/Documents/ProteanCopilot
```

4. 等待 Gradle 同步完成。
5. 如果 IDEA 提示 Gradle JVM，选择本机已有 JDK 17+ 即可。

项目使用的关键配置：

- [settings.gradle.kts](/Users/janker/Documents/ProteanCopilot/settings.gradle.kts)
- [build.gradle.kts](/Users/janker/Documents/ProteanCopilot/build.gradle.kts)
- [plugin.xml](/Users/janker/Documents/ProteanCopilot/src/main/resources/META-INF/plugin.xml)

## 方式一：使用 IDEA Run Configuration

项目已经包含运行配置：

```text
.run/Run IDE with Plugin.run.xml
```

在 IDEA 中执行：

1. 打开右上角 Run Configuration 下拉框。
2. 选择 `Run IDE with Plugin`。
3. 点击 Run 或 Debug。

运行后 IDEA 会启动一个独立的沙箱 IDE 实例，并自动安装当前插件。

在沙箱 IDE 中验证：

- 右侧 Tool Window 中应出现 `Protean Copilot`。
- 菜单栏 `Tools | Protean Copilot | Explain Selection` 应可点击。
- 在编辑器中选中代码后点击该菜单，应弹出通知，说明已采集当前选区上下文。

## 方式二：命令行运行插件

在项目根目录执行：

```bash
./gradlew runIde
```

这会启动一个带插件的沙箱 IntelliJ IDEA 实例。

如果只想验证构建任务是否正常：

```bash
./gradlew tasks
```

如果只想验证 Kotlin 源码编译：

```bash
./gradlew compileKotlin
```

如果要完整打包插件：

```bash
./gradlew buildPlugin
```

插件包输出位置：

```text
build/distributions/protean-copilot-0.1.0-SNAPSHOT.zip
```

## Debug 断点调试

调试插件时推荐用 IDEA 的 `Debug` 按钮启动 `Run IDE with Plugin`。

常用断点位置：

- [ExplainSelectionAction.kt](/Users/janker/Documents/ProteanCopilot/src/main/kotlin/com/protean/copilot/actions/ExplainSelectionAction.kt)
  - `actionPerformed`
  - `update`
- [ProteanToolWindowFactory.kt](/Users/janker/Documents/ProteanCopilot/src/main/kotlin/com/protean/copilot/ui/ProteanToolWindowFactory.kt)
  - `createToolWindowContent`
- [ProteanToolWindowPanel.kt](/Users/janker/Documents/ProteanCopilot/src/main/kotlin/com/protean/copilot/ui/ProteanToolWindowPanel.kt)
  - `notifyPlaceholder`

调试流程：

1. 在上述方法里打断点。
2. 用 Debug 模式启动 `Run IDE with Plugin`。
3. 在沙箱 IDE 中打开任意项目或文件。
4. 操作插件 UI 或菜单。
5. 主 IDEA 会命中断点。

## Split Mode 运行

JetBrains 新版插件开发强调兼容 Remote Development / Split Mode。当前 Gradle 插件已经提供相关任务：

```bash
./gradlew runIdeSplitMode
```

也可以分别运行：

```bash
./gradlew runIdeBackend
./gradlew runIdeFrontend
```

当前骨架代码只使用基础 Platform API 和 Swing UI，适合作为后续兼容 Split Mode 的起点。后续如果加入文件系统、索引、编辑器、终端或后端 Agent 调用，需要继续区分前端 UI 逻辑和后端项目逻辑。

## 手动安装打包插件

执行：

```bash
./gradlew buildPlugin
```

然后在 IDEA 中：

1. 打开 `Settings | Plugins`。
2. 点击齿轮图标。
3. 选择 `Install Plugin from Disk...`。
4. 选择：

```text
build/distributions/protean-copilot-0.1.0-SNAPSHOT.zip
```

5. 重启 IDEA。

这种方式适合验证插件包是否能被正常安装，但日常开发调试更推荐 `runIde`。

## 常见问题

### 1. 提示找不到 Java 21

如果看到类似错误：

```text
Cannot find a Java installation matching: {languageVersion=21}
```

先确认 `settings.gradle.kts` 中存在：

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
```

然后重新执行：

```bash
./gradlew javaToolchains
./gradlew buildPlugin
```

Gradle 会自动下载 JDK 21 toolchain。

### 2. Kotlin metadata 版本不兼容

如果看到：

```text
Module was compiled with an incompatible version of Kotlin
```

说明 Kotlin Gradle 插件版本低于目标 IDEA 平台依赖的 Kotlin metadata 版本。当前项目已使用 Kotlin `2.2.0`，对应配置在 `build.gradle.kts`：

```kotlin
id("org.jetbrains.kotlin.jvm") version "2.2.0"
```

### 3. 插件没有出现在 Tool Window

检查：

- `plugin.xml` 中是否注册了 `toolWindow`。
- `factoryClass` 是否指向 `com.protean.copilot.ui.ProteanToolWindowFactory`。
- 是否是通过 `runIde` 启动的沙箱 IDE，而不是普通打开当前项目的 IDEA。

### 4. 修改代码后沙箱 IDE 没变化

停止沙箱 IDE，重新执行：

```bash
./gradlew runIde
```

如果仍然异常，可以清理后再跑：

```bash
./gradlew clean runIde
```

## 当前可调试功能点

当前插件骨架已经有两个入口：

- Tool Window：右侧 `Protean Copilot` 面板，可输入 prompt 并点击 `Send`。
- Action：`Tools | Protean Copilot | Explain Selection`，会读取当前选区、当前文件或当前项目。

后续接入 Agent 后端时，建议优先沿这两个入口扩展：

- Tool Window 负责对话和任务控制。
- Action 负责从当前 IDE 上下文触发一次明确操作，例如解释选区、生成测试、修复错误。
