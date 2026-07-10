package com.protean.copilot.session;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.Alarm;
import com.protean.copilot.handler.core.HandlerContext;

import java.util.function.LongConsumer;

/**
 * 流式消息合并器 —— 批量合并消息更新以降低 WebView 推送频率。
 *
 * <p>在 AI 流式响应期间，消息更新非常频繁。如果每次 {@code onMessageUpdate()}
 * 都直接推送到 JCEF，大量 JSON 序列化和 V8 解析会拖垮渲染线程。
 * 本类通过 Swing Alarm 定时批量推送，节流间隔根据负载自适应。
 *
 * <p>同时发送 10 秒间隔的流式心跳，防止 {@code WebviewWatchdog} 在工具执行等
 * 长时间没有内容增量的阶段误触。
 */
public class StreamMessageCoalescer {

    private static final Logger LOG = Logger.getInstance(StreamMessageCoalescer.class);

    // ---- 间隔常量 ----
    private static final int UPDATE_INTERVAL_MS = 50;
    private static final int STREAMING_MIN_INTERVAL_MS = 150;
    private static final int MEDIUM_INTERVAL_MS = 500;
    private static final int LARGE_INTERVAL_MS = 2_000;
    private static final int XLARGE_INTERVAL_MS = 5_000;
    private static final int HEARTBEAT_INTERVAL_MS = 10_000;
    private static final int LARGE_PAYLOAD_THRESHOLD = 100_000;
    private static final int LARGE_UPDATE_PAYLOAD_CHARS = 150_000;
    private static final long SLOW_PAYLOAD_BUILD_MS = 25L;

    // ---- 状态 ----
    private final Object lock = new Object();
    private final Alarm updateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    private final Alarm heartbeatAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    private volatile boolean streamActive = false;
    private volatile boolean updateScheduled = false;
    private volatile long lastUpdateAtMs = 0L;
    private volatile long updateSequence = 0L;
    private volatile int lastPayloadChars = 0;
    private volatile String pendingMessagesJson = null;
    private volatile String lastSnapshot = null;

    private final JsCallbackTarget callbackTarget;

    /** 回调接口 —— 将数据推送到 JCEF WebView。 */
    public interface JsCallbackTarget {
        void callJavaScript(String functionName, String... args);
        JBCefBrowser getBrowser();
        boolean isDisposed();
        HandlerContext getHandlerContext();
    }

    public StreamMessageCoalescer(JsCallbackTarget callbackTarget) {
        this.callbackTarget = callbackTarget;
    }

    // ---- 公共 API ----

    /** 将消息 JSON 加入队列，调度合并推送。 */
    public void enqueue(String messagesJson) {
        if (callbackTarget.isDisposed()) return;
        synchronized (lock) { pendingMessagesJson = messagesJson; }
        schedulePush();
        if (streamActive) startHeartbeat();
    }

    public void onStreamStart() {
        synchronized (lock) { streamActive = true; }
        startHeartbeat();
    }

    public void onStreamEnd() {
        heartbeatAlarm.cancelAllRequests();
        synchronized (lock) { streamActive = false; lastPayloadChars = 0; }
    }

    public void resetStreamState() {
        updateAlarm.cancelAllRequests();
        heartbeatAlarm.cancelAllRequests();
        synchronized (lock) {
            streamActive = false; updateScheduled = false;
            pendingMessagesJson = null; lastSnapshot = null;
            lastUpdateAtMs = 0L; lastPayloadChars = 0;
            ++updateSequence;
        }
    }

    public boolean isStreamActive() { return streamActive; }

    public void flush(LongConsumer afterFlushOnEdt) {
        if (callbackTarget.isDisposed()) return;
        final String snapshot;
        final long sequence;
        synchronized (lock) {
            updateAlarm.cancelAllRequests();
            updateScheduled = false;
            snapshot = pendingMessagesJson != null ? pendingMessagesJson : lastSnapshot;
            pendingMessagesJson = null;
            sequence = ++updateSequence;
        }
        if (snapshot == null) {
            if (afterFlushOnEdt != null) {
                final long s = sequence;
                ApplicationManager.getApplication().invokeLater(() -> afterFlushOnEdt.accept(s));
            }
            return;
        }
        sendToWebView(snapshot, sequence, afterFlushOnEdt);
    }

    public void dispose() {
        try { updateAlarm.cancelAllRequests(); updateAlarm.dispose(); }
        catch (Exception e) { LOG.warn("Failed to dispose update alarm: " + e.getMessage()); }
        try { heartbeatAlarm.cancelAllRequests(); heartbeatAlarm.dispose(); }
        catch (Exception e) { LOG.warn("Failed to dispose heartbeat alarm: " + e.getMessage()); }
    }

    // ---- 内部 ----

    private int effectiveIntervalMs() {
        if (!streamActive) return UPDATE_INTERVAL_MS;
        int chars = lastPayloadChars;
        if (chars > 500_000) return XLARGE_INTERVAL_MS;
        if (chars > 200_000) return LARGE_INTERVAL_MS;
        if (chars > LARGE_PAYLOAD_THRESHOLD) return MEDIUM_INTERVAL_MS;
        return STREAMING_MIN_INTERVAL_MS;
    }

    private void schedulePush() {
        if (callbackTarget.isDisposed()) return;
        final int delayMs;
        synchronized (lock) {
            if (updateScheduled) return;
            int interval = effectiveIntervalMs();
            long elapsed = System.currentTimeMillis() - lastUpdateAtMs;
            delayMs = (int) Math.max(0L, interval - elapsed);
            updateScheduled = true;
            ++updateSequence;
        }
        updateAlarm.addRequest(() -> {
            final String snapshot;
            final long sequence;
            synchronized (lock) {
                updateScheduled = false;
                lastUpdateAtMs = System.currentTimeMillis();
                snapshot = pendingMessagesJson;
                pendingMessagesJson = null;
                sequence = updateSequence;
            }
            if (callbackTarget.isDisposed()) return;
            if (snapshot != null) sendToWebView(snapshot, sequence, null);
            boolean hasPending;
            synchronized (lock) { hasPending = pendingMessagesJson != null; }
            if (hasPending && !callbackTarget.isDisposed()) schedulePush();
        }, delayMs);
    }

    private void sendToWebView(String messagesJson, long sequence, LongConsumer afterSendOnEdt) {
        synchronized (lock) { lastSnapshot = messagesJson; }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            final int payloadChars;
            final long buildMs;
            final String callbackPayload;
            try {
                long start = System.nanoTime();
                payloadChars = messagesJson.length();
                // callJavaScript owns JavaScript string escaping at the JCEF boundary.
                // Escaping here as well makes JSON.parse receive {\\"...} instead of JSON.
                callbackPayload = prepareCallbackPayload(messagesJson);
                buildMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                lastPayloadChars = payloadChars;
                if (payloadChars >= LARGE_UPDATE_PAYLOAD_CHARS || buildMs >= SLOW_PAYLOAD_BUILD_MS) {
                    LOG.info("[WebviewTransport] chars=" + payloadChars + ", buildMs=" + buildMs + ", seq=" + sequence);
                }
            } catch (Exception e) {
                LOG.warn("Failed to prepare message JSON: " + e.getMessage());
                if (afterSendOnEdt != null) {
                    final long s = sequence;
                    ApplicationManager.getApplication().invokeLater(() -> afterSendOnEdt.accept(s));
                }
                return;
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                if (callbackTarget.isDisposed()) {
                    if (afterSendOnEdt != null) afterSendOnEdt.accept(sequence);
                    return;
                }
                synchronized (lock) {
                    if (sequence != updateSequence) {
                        if (afterSendOnEdt != null) afterSendOnEdt.accept(sequence);
                        return;
                    }
                }
                try {
                    callbackTarget.callJavaScript("updateMessages", callbackPayload, String.valueOf(sequence));
                } catch (Exception e) {
                    LOG.warn("Failed to push updateMessages (chars=" + callbackPayload.length() + "): " + e.getMessage());
                }
                if (afterSendOnEdt != null) afterSendOnEdt.accept(sequence);
            });
        });
    }

    /**
     * Returns the JSON snapshot unchanged. The callback target performs the single required
     * JavaScript-string escaping immediately before executing code in JCEF.
     */
    static String prepareCallbackPayload(String messagesJson) {
        return messagesJson;
    }

    // ---- 流式心跳 ----

    private void startHeartbeat() {
        heartbeatAlarm.cancelAllRequests();
        scheduleHeartbeat();
    }

    private void scheduleHeartbeat() {
        if (!streamActive || callbackTarget.isDisposed()) return;
        heartbeatAlarm.addRequest(() -> {
            if (!streamActive || callbackTarget.isDisposed()) return;
            try { callbackTarget.callJavaScript("onStreamingHeartbeat"); }
            catch (Exception e) { LOG.warn("[Heartbeat] Failed: " + e.getMessage()); }
            scheduleHeartbeat();
        }, HEARTBEAT_INTERVAL_MS);
    }
}
