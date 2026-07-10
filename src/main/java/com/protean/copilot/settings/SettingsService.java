package com.protean.copilot.settings;

import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.annotations.Nullable;

/**
 * 管理插件设置的读写，基于 IntelliJ {@link PropertiesComponent} 实现持久化。
 *
 * <p>所有设置项在 IDE 重启后保持不变。键名统一使用 {@code protean.} 前缀。
 *
 * <h3>支持的设置项</h3>
 * <ul>
 *   <li>权限模式（bypassPermissions / default / plan / acceptEdits）</li>
 *   <li>AI Provider（claude / protean）</li>
 *   <li>Node.js 可执行文件路径</li>
 *   <li>自定义桥接脚本路径</li>
 *   <li>项目级自定义工作目录</li>
 * </ul>
 */
public class SettingsService {

    // ---- 键名常量 ----
    private static final String KEY_PERMISSION_MODE = "protean.permissionMode";
    private static final String KEY_PROVIDER = "protean.provider";
    private static final String KEY_NODE_PATH = "protean.nodePath";
    private static final String KEY_BRIDGE_SCRIPT = "protean.bridgeScript";
    private static final String KEY_WORKING_DIR_PREFIX = "protean.workDir.";

    /** 将项目路径哈希为合法的 PropertiesComponent 键后缀 */
    private static String workingDirKey(String projectPath) {
        return KEY_WORKING_DIR_PREFIX + Integer.toHexString(projectPath.hashCode());
    }

    private final PropertiesComponent props = PropertiesComponent.getInstance();

    // ==================== 权限模式 ====================

    /**
     * 获取当前的权限模式设置。
     * @return 权限模式，默认 {@code "default"}
     */
    public String getPermissionMode() {
        return props.getValue(KEY_PERMISSION_MODE, "default");
    }

    public void setPermissionMode(String mode) {
        props.setValue(KEY_PERMISSION_MODE, mode);
    }

    // ==================== Provider ====================

    /**
     * 获取当前的 AI Provider 设置。
     * @return 提供商名称，默认 {@code "claude"}
     */
    public String getProvider() {
        return props.getValue(KEY_PROVIDER, "claude");
    }

    public void setProvider(String provider) {
        props.setValue(KEY_PROVIDER, provider);
    }

    // ==================== Node.js 路径 ====================

    /**
     * 获取 Node.js 可执行文件的路径。
     * @return Node.js 路径，默认 {@code "node"}（系统 PATH）
     */
    public String getNodePath() {
        return props.getValue(KEY_NODE_PATH, "node");
    }

    public void setNodePath(String path) {
        props.setValue(KEY_NODE_PATH, path);
    }

    // ==================== 桥接脚本路径 ====================

    /**
     * 获取自定义桥接脚本路径。
     * @return 自定义路径，或 null 表示使用 classpath 内嵌脚本
     */
    @Nullable
    public String getBridgeScriptPath() {
        return props.getValue(KEY_BRIDGE_SCRIPT);
    }

    public void setBridgeScriptPath(@Nullable String path) {
        if (path != null) {
            props.setValue(KEY_BRIDGE_SCRIPT, path);
        } else {
            props.unsetValue(KEY_BRIDGE_SCRIPT);
        }
    }

    // ==================== 自定义工作目录 ====================

    /**
     * 获取项目路径对应的自定义工作目录。
     * @param projectPath 项目根路径
     * @return 自定义工作目录，未配置时返回 null
     */
    @Nullable
    public String getCustomWorkingDirectory(String projectPath) {
        return props.getValue(workingDirKey(projectPath));
    }

    public void setCustomWorkingDirectory(String projectPath, @Nullable String workingDir) {
        if (workingDir != null) {
            props.setValue(workingDirKey(projectPath), workingDir);
        } else {
            props.unsetValue(workingDirKey(projectPath));
        }
    }
}
