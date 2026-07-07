package com.protean.copilot.permission;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限对话框和访问控制的占位实现。
 * 当 SDK 后端连接后，将管理权限请求。
 */
public class PermissionService {

    private static final Logger LOG = Logger.getInstance(PermissionService.class);
    private static final ConcurrentHashMap<String, PermissionService> instanceCache = new ConcurrentHashMap<>();

    private final Project project;
    private final String sessionId;

    public PermissionService(Project project, String sessionId) {
        this.project = project;
        this.sessionId = sessionId;
    }

    /**
     * 为项目和会话获取或创建一个 PermissionService 实例。
     */
    public static PermissionService getInstance(Project project, String sessionId) {
        String key = project.getLocationHash() + "_" + sessionId;
        return instanceCache.computeIfAbsent(key, k -> new PermissionService(project, sessionId));
    }

    /**
     * 移除指定会话的 PermissionService 实例。
     */
    public static void removeInstance(String sessionId) {
        instanceCache.values().removeIf(entry -> entry.sessionId.equals(sessionId));
        LOG.info("Removed PermissionService instance for session: " + sessionId);
    }

    /**
     * 为项目取消注册对话框显示器。
     */
    public void unregisterDialogShower(Project project) {
        // 桩实现
    }

    /**
     * 清除权限决策记忆。
     */
    public void clearDecisionMemory() {
        // 桩实现
    }
}
