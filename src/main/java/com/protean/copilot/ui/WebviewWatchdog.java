package com.protean.copilot.ui;

import com.protean.copilot.util.HtmlLoader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.concurrency.AppExecutorUtil;

import javax.swing.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Webview 渲染看门狗，用于 JCEF 卡顿/黑屏恢复。
 *
 * 通过前端发出的心跳信号触发定期健康检查来监控 webview。
 * 如果前端停止发送心跳，看门狗将首先尝试重新加载 HTML，
 * 然后升级为完全重建浏览器。
 *
 * 移植自参考实现。
 */
public class WebviewWatchdog {

    private static final Logger LOG = Logger.getInstance(WebviewWatchdog.class);

    /** 自上次心跳以来的时间，超过此时间则认为 webview 卡顿。 */
    private static final long HEARTBEAT_TIMEOUT_MS = 45_000L;

    /** 看门狗检查健康状态的频率。 */
    private static final long WATCHDOG_INTERVAL_MS = 10_000L;

    /** 两次恢复尝试之间的最短间隔。 */
    private static final long RECOVERY_COOLDOWN_MS = 60_000L;

    /** 流式传输进行时的延长心跳超时（3 分钟）。 */
    private static final long STREAMING_HEARTBEAT_TIMEOUT_MS = 180_000L;

    @FunctionalInterface
    public interface BrowserProvider {
        JBCefBrowser getBrowser();
    }

    @FunctionalInterface
    public interface DisposedCheck {
        boolean isDisposed();
    }

    @FunctionalInterface
    public interface StreamActiveCheck {
        boolean isStreamActive();
    }

    private final JPanel mainPanel;
    private final BrowserProvider browserProvider;
    private final HtmlLoader htmlLoader;
    private final java.util.function.Consumer<String> onRecreateWebview;
    private final DisposedCheck disposedCheck;
    private final StreamActiveCheck streamActiveCheck;

    private volatile long lastHeartbeatAt = System.currentTimeMillis();
    private volatile long lastRafAt = System.currentTimeMillis();
    private volatile long lastRecoveryAt = 0L;

    private int stallCount = 0;
    private ScheduledFuture<?> scheduledFuture = null;

    public WebviewWatchdog(
        JPanel mainPanel,
        BrowserProvider browserProvider,
        HtmlLoader htmlLoader,
        java.util.function.Consumer<String> onRecreateWebview,
        DisposedCheck disposedCheck,
        StreamActiveCheck streamActiveCheck
    ) {
        this.mainPanel = mainPanel;
        this.browserProvider = browserProvider;
        this.htmlLoader = htmlLoader;
        this.onRecreateWebview = onRecreateWebview;
        this.disposedCheck = disposedCheck;
        this.streamActiveCheck = streamActiveCheck;
    }

    /**
     * 启动看门狗。安排定期健康检查。
     */
    public void start() {
        if (disposedCheck.isDisposed()) return;
        resetTimestamps();
        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(
                () -> checkHealth(),
                WATCHDOG_INTERVAL_MS,
                WATCHDOG_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
        LOG.info("WebviewWatchdog started");
    }

    /**
     * 停止看门狗。取消定期健康检查。
     */
    public void stop() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        scheduledFuture = null;
        LOG.info("WebviewWatchdog stopped");
    }

    /**
     * 处理来自前端的心跳信号。
     * 当前端发送心跳消息时调用。
     */
    public void handleHeartbeat(String content) {
        try {
            if (content.startsWith("{")) {
                // JSON 心跳 —— 提取 raf 时间戳
                String rafValue = extractJsonValue(content, "raf");
                if (rafValue != null) {
                    try {
                        lastRafAt = Long.parseLong(rafValue);
                    } catch (NumberFormatException ignored) {
                        lastRafAt = System.currentTimeMillis();
                    }
                }
            }
        } catch (Exception ignored) {
            // 非 JSON 心跳（向后兼容） —— 仅更新时间戳
        }
        lastHeartbeatAt = System.currentTimeMillis();
    }

    /**
     * 将所有时间戳重置为当前时间。
     */
    public void resetTimestamps() {
        long now = System.currentTimeMillis();
        lastHeartbeatAt = now;
        lastRafAt = now;
    }

    private void checkHealth() {
        if (disposedCheck.isDisposed()) return;

        // 如果主面板未显示则退出
        if (!mainPanel.isShowing()) return;

        // 如果 webview 隐藏则退出
        JBCefBrowser browser = browserProvider.getBrowser();
        if (browser == null || !browser.getComponent().isShowing()) return;

        // 如果在恢复冷却期内则退出
        long now = System.currentTimeMillis();
        if (now - lastRecoveryAt < RECOVERY_COOLDOWN_MS) return;

        // 流式传输进行时使用更长的超时
        long timeout = streamActiveCheck.isStreamActive()
            ? STREAMING_HEARTBEAT_TIMEOUT_MS
            : HEARTBEAT_TIMEOUT_MS;

        long heartbeatAge = now - lastHeartbeatAt;

        if (heartbeatAge > timeout) {
            LOG.warn("Webview watchdog: heartbeat stall detected (age=" + heartbeatAge
                + "ms, timeout=" + timeout + "ms, stallCount=" + stallCount + ")");

            lastRecoveryAt = now;
            stallCount++;

            ApplicationManager.getApplication().invokeLater(() -> {
                if (disposedCheck.isDisposed()) return;

                if (stallCount <= 1) {
                    // 首次卡顿：尝试重新加载 HTML
                    LOG.info("Webview watchdog: attempting HTML reload");
                    JBCefBrowser currentBrowser = browserProvider.getBrowser();
                    if (currentBrowser != null) {
                        try {
                            String html = htmlLoader.loadChatHtml();
                            currentBrowser.loadHTML(html);
                        } catch (Exception e) {
                            LOG.warn("Failed to reload HTML in watchdog: " + e.getMessage());
                        }
                    }
                } else {
                    // 第二次及以上卡顿：完全重建浏览器
                    LOG.info("Webview watchdog: attempting full browser recreate");
                    onRecreateWebview.accept("watchdog_recreate");
                }
                resetTimestamps();
            });
        }
    }

    private String extractJsonValue(String json, String key) {
        // 简单的 JSON 值提取，无需完整解析
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.eE]+)");
        var matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
