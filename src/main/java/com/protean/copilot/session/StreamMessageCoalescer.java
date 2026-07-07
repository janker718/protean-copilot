package com.protean.copilot.session;

import com.protean.copilot.handler.HandlerContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.jcef.JBCefBrowser;
import java.util.function.LongConsumer;

/**
 * 合并流式消息更新以限制 webview 推送频率。
 * 将快速的 onMessageUpdate 回调批量处理为周期性的 UI 刷新，
 * 以避免压垮 JCEF 浏览器。
 *
 * 当前为存根，可在 SDK 后端连接后完整实现。
 */
public class StreamMessageCoalescer {

    private static final Logger LOG = Logger.getInstance(StreamMessageCoalescer.class);

    /**
     * 用于向 webview 推送数据的回调接口。
     */
    public interface JsCallbackTarget {
        void callJavaScript(String functionName, String... args);
        JBCefBrowser getBrowser();
        boolean isDisposed();
        HandlerContext getHandlerContext();
    }

    private final JsCallbackTarget callbackTarget;
    private volatile boolean streamActive = false;

    public StreamMessageCoalescer(JsCallbackTarget callbackTarget) {
        this.callbackTarget = callbackTarget;
    }

    /**
     * 通知流已开始。
     */
    public void onStreamStart() {
        streamActive = true;
    }

    /**
     * 通知流已结束。
     */
    public void onStreamEnd() {
        streamActive = false;
    }

    /**
     * 重置流状态（例如，在创建新会话时）。
     */
    public void resetStreamState() {
        streamActive = false;
    }

    /**
     * 检查流当前是否处于活动状态。
     */
    public boolean isStreamActive() {
        return streamActive;
    }

    /**
     * 立即刷新所有待处理的消息。
     */
    public void flush(LongConsumer afterFlushOnEdt) {
        if (callbackTarget.isDisposed()) return;
        if (afterFlushOnEdt != null) {
            ApplicationManager.getApplication().invokeLater(() -> afterFlushOnEdt.accept(0L));
        }
    }

    /**
     * 释放内部资源。
     */
    public void dispose() {
        streamActive = false;
    }
}
