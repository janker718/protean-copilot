# Protean Copilot

Protean Copilot is an IntelliJ IDEA plugin skeleton for building a programming assistant.

It follows the JetBrains IntelliJ Platform Plugin SDK Gradle project layout:

- `build.gradle.kts` uses `org.jetbrains.intellij.platform` 2.x with Gradle 9.
- `src/main/resources/META-INF/plugin.xml` declares plugin metadata, actions, notifications, and a Tool Window.
- `src/main/kotlin` contains the initial Kotlin plugin implementation.

## Current Features

- `Protean Copilot` Tool Window on the right side of the IDE.
- `Tools | Protean Copilot | Explain Selection` action.
- Basic access to the current project, selected editor text, and current file.

## Run

Use the Gradle task:

```bash
./gradlew runIde
```

This starts a sandbox IntelliJ IDEA instance with the plugin installed.

## Next Steps

- Add context collection for current file, selected code, Git diff, test output, and compiler errors.
- Add a local Agent runtime API client.
- Show generated edits as a diff before applying them.
- Add tests around context building and action behavior.
