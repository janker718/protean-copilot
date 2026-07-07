package com.protean.copilot.ui;

import com.protean.copilot.util.JsUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * 跟踪编辑器上下文（当前文件、选区）并通知前端。
 * 处理文件切换和文本选区变更，并进行防抖更新。
 *
 * 移植自参考实现。
 */
public class EditorContextTracker {

    private static final Logger LOG = Logger.getInstance(EditorContextTracker.class);
    private static final long DEBOUNCE_MS = 200;

    /**
     * 上下文变更的回调接口。
     * 不能是 fun interface，因为它有两个抽象方法。
     */
    public interface ContextCallback {
        void addSelectionInfo(String info);
        void clearSelectionInfo();
    }

    private final Project project;
    private final ContextCallback callback;
    private final com.intellij.util.messages.MessageBusConnection messageBusConnection;

    private volatile boolean disposed = false;
    private long lastUpdateTime = 0L;

    public EditorContextTracker(Project project, ContextCallback callback) {
        this.project = project;
        this.callback = callback;
        this.messageBusConnection = project.getMessageBus().connect();
    }

    /**
     * 注册文件切换和文本选区变更的监听器。
     */
    public void registerListeners() {
        // 监听文件编辑器变更（文件切换）
        messageBusConnection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            new FileEditorManagerListener() {
                @Override
                public void fileOpened(FileEditorManager source, VirtualFile file) {
                    scheduleContextUpdate();
                }

                @Override
                public void fileClosed(FileEditorManager source, VirtualFile file) {
                    scheduleContextUpdate();
                }
            }
        );

        // 监听文本选区变更
        var eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
        eventMulticaster.addSelectionListener(new SelectionListener() {
            @Override
            public void selectionChanged(SelectionEvent e) {
                scheduleContextUpdate();
            }
        }, project);

        LOG.info("EditorContextTracker listeners registered for project: " + project.getName());
    }

    private void scheduleContextUpdate() {
        if (disposed) return;

        long now = System.currentTimeMillis();
        if (now - lastUpdateTime < DEBOUNCE_MS) return;
        lastUpdateTime = now;

        ApplicationManager.getApplication().invokeLater(() -> {
            if (!disposed) {
                updateContextInfo();
            }
        });
    }

    private void updateContextInfo() {
        if (disposed) return;

        ApplicationManager.getApplication().invokeLater(() -> {
            if (disposed) return;

            try {
                var editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                var file = FileEditorManager.getInstance(project).getSelectedEditor() != null
                    ? FileEditorManager.getInstance(project).getSelectedEditor().getFile()
                    : null;

                if (editor != null && file != null) {
                    String path = file.getPath();
                    var selectionModel = editor.getSelectionModel();
                    if (selectionModel.hasSelection()) {
                        int startLine = editor.getDocument().getLineNumber(selectionModel.getSelectionStart()) + 1;
                        int endLine = editor.getDocument().getLineNumber(selectionModel.getSelectionEnd()) + 1;
                        String selectedText = selectionModel.getSelectedText() != null ? selectionModel.getSelectedText() : "";
                        String info = "{\"path\":\"" + JsUtils.escapeJs(path) + "\",\"startLine\":" + startLine
                            + ",\"endLine\":" + endLine + ",\"text\":\"" + JsUtils.escapeJs(selectedText) + "\"}";
                        callback.addSelectionInfo(info);
                    } else {
                        String info = "{\"path\":\"" + JsUtils.escapeJs(path) + "\"}";
                        callback.addSelectionInfo(info);
                    }
                } else {
                    callback.clearSelectionInfo();
                }
            } catch (Exception e) {
                LOG.warn("Failed to update context info: " + e.getMessage());
            }
        });
    }

    /**
     * 释放跟踪器并断开监听器连接。
     */
    public void dispose() {
        disposed = true;
        messageBusConnection.disconnect();
        LOG.info("EditorContextTracker disposed for project: " + project.getName());
    }
}
