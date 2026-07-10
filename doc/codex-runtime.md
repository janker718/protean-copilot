# Codex Runtime / Dependency Notes

更新时间：2026-07-10

## 锁定版本

- WebView dependency: `@openai/codex-sdk@0.143.0`
- Java dependency backend locked version: `0.143.0`
- Fallback versions: `0.143.0`, `0.142.5`, `0.142.4`

这三个位置必须保持一致：

- [webview/package.json](/Users/janker/Documents/ProteanCopilot/webview/package.json)
- [webview/package-lock.json](/Users/janker/Documents/ProteanCopilot/webview/package-lock.json)
- [SdkDefinition.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/dependency/SdkDefinition.java)

## 运行环境

- Node.js: `>= 18`
- npm: 需要可执行，且最好与 Node 来自同一安装目录
- IDE bridge runtime:
  - Java 侧使用 [NodeDetector.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/NodeDetector.java) 探测 Node/npm
  - Node 侧使用 [codex-sdk-bridge.mjs](/Users/janker/Documents/ProteanCopilot/src/main/resources/bridge/codex-sdk-bridge.mjs) `import('@openai/codex-sdk')`

## 安装与锁版

在 `webview/` 下执行：

```bash
npm install @openai/codex-sdk@0.143.0 --save-exact
```

如果需要重新校验 lockfile 一致性：

```bash
npm ls @openai/codex-sdk
```

## 当前稳定化口径

- bridge ready 会返回：
  - `sdkAvailable`
  - `version`
  - `runtime`
  - `sdkPackage`
  - `importError`
  - `hint`
- interrupt 没有活跃运行时，不再静默失败，会回传 status
- query / resume / interrupt / shutdown / prewarm 都有统一 status 或 error 口径
- Java 侧会把 provider、phase、sessionId、hint 统一写入日志，并向前端输出统一错误文案
- history resume、permission denied、sandbox denied 三类用户可感知错误已统一到 `SessionRuntimeMessages`

## 本轮回归结论

- `compileJava` 已通过
- Codex runtime / session / permission / dependency 相关 Java 定向测试已通过
- WebView `window callbacks / session management / permission dialog / dependency section / codex provider / error matcher` 定向测试已通过
- `./gradlew runIde` 已成功返回，说明当前插件沙箱可启动

需要明确：

- `runIde` 成功不等于“IDE 内完整人工点击回归已完成”
- 当前更详细的自动化证据、已覆盖矩阵和待人工项，见
  [codex-runtime-regression.md](/Users/janker/Documents/ProteanCopilot/doc/codex-runtime-regression.md)

## 常见失败场景

### `No matching version found`

说明锁定版本写成了 registry 上不存在的版本。先查：

```bash
npm view @openai/codex-sdk versions --json
```

再同步更新 Java 锁定版本、`package.json` 和 `package-lock.json`。

### `sdkAvailable=false`

优先检查：

1. `webview/node_modules/@openai/codex-sdk` 是否存在
2. Node 版本是否 `>= 18`
3. IDE 实际使用的 Node 是否与命令行一致

### prewarm / query / resume 失败

优先检查：

1. provider 鉴权
2. sandbox / approval 设置
3. 当前工作目录是否可访问
4. 锁定 SDK 版本与 bridge 行为是否一致
