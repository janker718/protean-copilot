package com.protean.copilot.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 持久化标签页会话状态，基于 IntelliJ {@link PersistentStateComponent}。
 *
 * <p>状态以 XML 格式存储在 {@code proteanTabState.xml} 中，项目级别。
 * IDE 重启后自动恢复。
 */
@State(
    name = "ProteanTabState",
    storages = @Storage("proteanTabState.xml")
)
@Service(Service.Level.PROJECT)
public final class TabStateService implements PersistentStateComponent<TabStateService.State> {

    private static final Logger LOG = Logger.getInstance(TabStateService.class);

    /**
     * 获取项目级 TabStateService 实例。
     */
    public static TabStateService getInstance(@NotNull Project project) {
        return project.getService(TabStateService.class);
    }

    // ---- 内部状态 Bean ----

    /**
     * 序列化到 XML 的状态对象。
     */
    public static class State {
        public Map<Integer, TabSessionState> tabSessions = new HashMap<>();
        public int tabCount = 1;
    }

    /**
     * 表示标签页的会话快照。
     */
    public record TabSessionState(
        String provider,
        String sessionId,
        String cwd,
        String model,
        String permissionMode,
        String reasoningEffort
    ) {
        public TabSessionState() {
            this("claude", null, null, "default", "bypassPermissions", null);
        }
    }

    // ---- 运行时状态 ----

    private State state = new State();

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loaded) {
        this.state = loaded;
    }

    // ---- 公共 API ----

    public void saveTabSessionState(int index, TabSessionState sessionState) {
        state.tabSessions.put(index, sessionState);
        LOG.info("Saved tab session state at index " + index);
    }

    @Nullable
    public TabSessionState getTabSessionState(int index) {
        return state.tabSessions.get(index);
    }

    public void clearTabSessionState(int index) {
        state.tabSessions.remove(index);
    }

    public int getTabCount() {
        return state.tabCount;
    }

    public void setTabCount(int count) {
        state.tabCount = count;
    }
}
