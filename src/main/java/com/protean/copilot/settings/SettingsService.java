package com.protean.copilot.settings;

import org.jetbrains.annotations.Nullable;

/**
 * 管理插件设置。
 * 提供权限模式、提供商、Node.js 路径等配置项的读写接口。
 * 当前大部分设置返回合理默认值，后续将扩展支持真正的持久化存储。
 */
public class SettingsService {

    /**
     * 获取当前的权限模式设置。
     *
     * @return 权限模式（"bypassPermissions" / "default" / "plan" / "acceptEdits"）
     */
    public String getPermissionMode() {
        return "bypassPermissions";
    }

    /**
     * 设置权限模式。
     *
     * @param mode 权限模式标识符
     */
    public void setPermissionMode(String mode) {
        // 桩：持久化尚未实现
    }

    /**
     * 获取项目路径的自定义工作目录（如果已配置）。
     *
     * @param projectPath 项目根路径
     * @return 自定义工作目录路径，如果未配置则返回 null
     */
    @Nullable
    public String getCustomWorkingDirectory(String projectPath) {
        return null;  // 桩：未配置自定义目录
    }

    /**
     * 获取当前的 provider 设置。
     *
     * @return 提供商名称（如 "claude"、"protean"）
     */
    public String getProvider() {
        return "protean";
    }

    /**
     * 获取 Node.js 可执行文件的路径。
     * 默认使用系统 PATH 中的 "node"。
     * 后续可扩展为从插件设置中读取用户配置的 Node.js 路径。
     *
     * @return Node.js 可执行文件路径
     */
    public String getNodePath() {
        return "node";
    }

    /**
     * 获取 Claude SDK 桥接脚本的路径。
     * 默认返回 null，表示使用 classpath 中的内嵌脚本。
     * 后续可扩展为允许用户指定自定义脚本路径。
     *
     * @return 自定义桥接脚本路径，如果使用内嵌脚本则返回 null
     */
    @Nullable
    public String getBridgeScriptPath() {
        return null;  // 使用 classpath 内的默认脚本
    }
}
