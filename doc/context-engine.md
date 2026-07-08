# 上下文引擎

> 本文档描述 Protean Copilot 的 IDE 上下文采集策略。当前为基础实现，待后续扩展。

---

## 当前实现

### IdeContextCollector

`IdeContextCollector` 通过 IntelliJ PSI API 收集结构化上下文，生成 `IdeContext` record：

```java
public record IdeContext(
    String projectName,
    String projectBasePath,
    CurrentFile currentFile,
    Selection selection,
    List<String> openFiles
) {}
```

采集来源：
- `Project` / `AnActionEvent` → 项目名称和根路径。
- `Editor` / `FileEditorManager` → 当前文件和选区。
- `VirtualFile` / PSI → 文件类型、语言、全文内容。

### EditorContextTracker

`EditorContextTracker` 实时监听编辑器变化：
- 监听 `FileEditorManager` 的文件切换事件。
- 监听编辑器选区变化（`SelectionModel` 和 `CaretModel`）。
- 200ms 防抖，避免高频推送。
- 通过 `callJavaScript("addSelectionInfo", ...)` 推送到前端。

### 当前覆盖的上下文

| 维度 | 状态 |
|---|---|
| 项目名称和根路径 | ✅ |
| 当前文件路径、语言、全文 | ✅ |
| 当前选区文本 | ✅ |
| 打开文件列表 | ✅ |
| 光标附近代码窗口 | 🔜 待实现 |
| Git diff | 🔜 待实现 |
| 最近一次构建/测试输出 | 🔜 待实现 |
| PSI 符号（类、方法、引用） | 🔜 待实现 |

---

## 后续扩展

- **PSI 符号分析**: 解析当前光标所在的方法、类、引用关系，比纯文本更可靠。
- **Git 上下文**: 当前分支、变更文件、diff 内容。
- **构建上下文**: 最近编译错误、测试失败信息。
- **项目结构索引**: 模块依赖关系、包结构，语义检索支持。
- **上下文裁剪**: 当上下文超长时，按优先级裁剪（项目信息 > 当前文件 > 选区 > 历史消息）。

---

## 相关文件

- [IdeContextCollector.java](../src/main/java/com/protean/copilot/context/IdeContextCollector.java)
- [IdeContext.java](../src/main/java/com/protean/copilot/context/IdeContext.java)
- [EditorContextTracker.java](../src/main/java/com/protean/copilot/ui/EditorContextTracker.java)
