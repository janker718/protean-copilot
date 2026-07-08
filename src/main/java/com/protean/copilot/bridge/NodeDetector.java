package com.protean.copilot.bridge;

import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.provider.claude.NodeDetectionResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Node.js 检测器 —— 定位并验证 Node.js 可执行文件。
 * 单例模式，缓存结果供 ClaudeSDKBridge 和其他组件共享。
 */
public class NodeDetector {

    private static final Logger LOG = Logger.getInstance(NodeDetector.class);

    // Windows 常见 Node.js 安装路径
    private static final String[] WINDOWS_NODE_PATHS = {
        "C:\\Program Files\\nodejs\\node.exe",
        "C:\\Program Files (x86)\\nodejs\\node.exe",
        System.getenv("APPDATA") + "\\nvm\\current\\node.exe",
    };

    private static volatile NodeDetector instance;
    private static final Object lock = new Object();

    private volatile String cachedNodeVersion;
    private volatile String cachedNodePath;
    private volatile String cachedNpmVersion;

    private NodeDetector() {}

    /** 获取单例实例。 */
    public static NodeDetector getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) instance = new NodeDetector();
            }
        }
        return instance;
    }

    /** 清除缓存。 */
    public void clearCache() {
        cachedNodeVersion = null;
        cachedNodePath = null;
        cachedNpmVersion = null;
    }

    // ---- 自动检测 ----

    /** 自动检测 Node.js 并缓存结果。 */
    public NodeDetectionResult detectNodeWithDetails() {
        // 先尝试 PATH 中的 "node"
        NodeDetectionResult result = verifyNodePath("node");
        if (result.available()) {
            cacheResult("node", result);
            return result;
        }

        // Windows: 尝试常见路径
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            for (String path : WINDOWS_NODE_PATHS) {
                if (path != null && Files.isRegularFile(Path.of(path))) {
                    result = verifyNodePath(path);
                    if (result.available()) {
                        cacheResult(path, result);
                        return result;
                    }
                }
            }
        }

        return NodeDetectionResult.failure("node", "Node.js not found on PATH");
    }

    // ---- 验证指定路径 ----

    /** 验证指定路径的 Node.js 并返回版本。 */
    public NodeDetectionResult verifyNodePath(String nodePath) {
        try {
            Process p = new ProcessBuilder(nodePath, "--version")
                .redirectErrorStream(true).start();
            String version;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                version = r.readLine();
            }
            p.waitFor(5, TimeUnit.SECONDS);
            if (version != null && !version.isEmpty()) {
                String npmVersion = detectNpmVersion();
                LOG.info("Node.js " + version.trim() + " detected at " + nodePath);
                return NodeDetectionResult.success(nodePath, version.trim(), npmVersion);
            }
        } catch (Exception e) {
            LOG.warn("Node.js verification failed for " + nodePath + ": " + e.getMessage());
        }
        return NodeDetectionResult.failure(nodePath, "Node.js not found or failed to execute");
    }

    /** 验证并缓存 Node.js 路径。 */
    public NodeDetectionResult verifyAndCacheNodePath(String path) {
        NodeDetectionResult result = verifyNodePath(path);
        if (result.available()) cacheResult(path, result);
        return result;
    }

    private void cacheResult(String path, NodeDetectionResult result) {
        cachedNodePath = path;
        cachedNodeVersion = result.version();
        cachedNpmVersion = result.npmVersion();
    }

    private String detectNpmVersion() {
        try {
            Process p = new ProcessBuilder("npm", "--version")
                .redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                return r.readLine();
            }
        } catch (Exception ignored) { return null; }
    }

    // ---- 缓存访问 ----

    public String getCachedNodeVersion() { return cachedNodeVersion; }
    public String getCachedNodePath() { return cachedNodePath; }
}
