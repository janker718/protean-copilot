package com.protean.copilot.ui.toolwindow;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 缓存在 webview 前端就绪之前到达的代码片段。
 *
 * 外部调用者（例如编辑器操作）可能在 JCEF webview 仍在加载时推送代码片段。
 * 该片段会暂存在此处，并在前端发出就绪信号后刷新。
 *
 * 线程安全：{@link #offer} 和 {@link #takePending} 使用原子交换，
 * 因此即使多个前端就绪回调同时触发，延迟片段也只会被发送恰好一次。
 */
public class PendingCodeSnippetBuffer {

    private final AtomicReference<String> pending = new AtomicReference<>();

    /**
     * 记录一个待显示的代码片段。
     *
     * @param snippet       要显示的片段（调用者必须传递非空值）
     * @param frontendReady webview 前端是否已就绪可以接收该片段
     * @return 当 {@code frontendReady} 为 {@code true} 时，返回应立即发送的片段；
     *         当片段被延迟到前端就绪时，返回 {@code null}
     */
    @Nullable
    public String offer(String snippet, boolean frontendReady) {
        if (frontendReady) {
            return snippet;
        }
        pending.set(snippet);
        return null;
    }

    /**
     * 原子地取出延迟的片段并清空缓冲区。
     *
     * @return 延迟的片段，如果没有待处理的片段则返回 {@code null}
     */
    @Nullable
    public String takePending() {
        return pending.getAndSet(null);
    }
}
