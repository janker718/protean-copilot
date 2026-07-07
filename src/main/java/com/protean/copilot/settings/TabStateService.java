package com.protean.copilot.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持久化和恢复标签页会话状态。
 * 从参考实现的 TabStateService 移植而来。
 */
public class TabStateService {

    private static final Logger LOG = Logger.getInstance(TabStateService.class);
    private static final ConcurrentHashMap<String, TabStateService> instanceCache = new ConcurrentHashMap<>();

    /**
     * 获取或创建给定项目的 TabStateService 实例。
     */
    public static TabStateService getInstance(Project project) {
        String key = String.valueOf(project.getLocationHash());
        return instanceCache.computeIfAbsent(key, k -> new TabStateService());
    }

    /**
     * 表示标签页会话的已保存状态。
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
            this("protean", null, null, "default", "bypassPermissions", null);
        }
    }

    private final ConcurrentHashMap<Integer, TabSessionState> stateMap = new ConcurrentHashMap<>();

    /**
     * 保存给定索引处标签页的会话状态。
     */
    public void saveTabSessionState(int index, TabSessionState state) {
        stateMap.put(index, state);
        LOG.info("Saved tab session state at index " + index + ": provider=" + state.provider() + ", sessionId=" + state.sessionId());
    }

    /**
     * 获取给定索引处标签页的会话状态。
     * @return 已保存的状态，如果此索引没有状态则返回 null
     */
    public TabSessionState getTabSessionState(int index) {
        return stateMap.get(index);
    }

    /**
     * 清除标签页的会话状态。
     */
    public void clearTabSessionState(int index) {
        stateMap.remove(index);
    }
}
