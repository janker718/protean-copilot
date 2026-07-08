package com.protean.copilot.handler.diff;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.editor.ChainDiffVirtualFile;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.Side;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 交互式差异管理器 —— 显示带 Apply/Reject 按钮的 IntelliJ 差异视图。
 * 参照 Claude Code JetBrains 官方插件实现。
 */
public class InteractiveDiffManager {

    private static final Logger LOG = Logger.getInstance(InteractiveDiffManager.class);
    private static final long DISMISS_DELAY_MS = 600;

    /**
     * 显示交互式差异视图。
     *
     * @return 在用户操作（Apply/Reject/Dismiss）时完成的 CompletableFuture
     */
    public static CompletableFuture<DiffResult> showInteractiveDiff(
            @NotNull Project project,
            @NotNull InteractiveDiffRequest request) {

        CompletableFuture<DiffResult> future = new CompletableFuture<>();
        if (project.isDisposed()) { future.complete(DiffResult.reject()); return future; }

        ApplicationManager.getApplication().invokeLater(() -> {
            try { showInternal(project, request, future); }
            catch (Exception e) { LOG.error("Failed to show interactive diff", e); future.complete(DiffResult.reject()); }
        });
        return future;
    }

    private static void showInternal(Project project, InteractiveDiffRequest req, CompletableFuture<DiffResult> future) {
        String original = LineSeparatorUtil.normalizeToLF(req.getOriginalContent());
        String proposed = LineSeparatorUtil.normalizeToLF(req.getNewFileContents());

        String fileName = new File(req.getFilePath()).getName();
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);

        VirtualFile actualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(req.getFilePath());
        if (actualFile != null && actualFile.exists()) {
            fileType = actualFile.getFileType() != FileTypes.UNKNOWN ? actualFile.getFileType() : fileType;
        }

        LightVirtualFile proposedFile = new LightVirtualFile(fileName, fileType, proposed);
        proposedFile.setDetectedLineSeparator("\n");

        DiffContentFactory cf = DiffContentFactory.getInstance();
        DiffContent left = cf.create(project, original, fileType);
        DocumentContent right = cf.createDocument(project, proposedFile);
        if (right == null) {
            future.complete(DiffResult.reject()); return;
        }

        String leftTitle = req.isNewFile() ? "New File" : "Original";
        SimpleDiffRequest diffReq = new SimpleDiffRequest(req.getTabName(), left, right, leftTitle, "Proposed");
        diffReq.putUserData(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS, new boolean[]{true, req.isReadOnly()});
        diffReq.putUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE, Side.RIGHT);

        SimpleDiffRequestChain chain = new SimpleDiffRequestChain(diffReq);
        AtomicBoolean acted = new AtomicBoolean(false);
        AtomicReference<ScheduledFuture<?>> dismissFuture = new AtomicReference<>();
        MessageBusConnection conn = project.getMessageBus().connect();

        AnAction rejectAction = new AnAction("Reject", "Reject changes", AllIcons.Actions.Cancel) {
            @Override public void actionPerformed(@NotNull AnActionEvent e) {
                if (acted.compareAndSet(false, true)) { conn.disconnect(); future.complete(DiffResult.reject()); }
            }
            @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
        };

        AnAction applyAction = new AnAction("Apply", "Apply changes", AllIcons.Actions.Checked) {
            @Override public void actionPerformed(@NotNull AnActionEvent e) {
                if (acted.compareAndSet(false, true)) {
                    conn.disconnect();
                    cancelDismiss(dismissFuture);
                    future.complete(DiffResult.apply(getContent(right, proposedFile)));
                    closeView(project, chain);
                }
            }
            @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
        };

        List<AnAction> actions = new ArrayList<>();
        actions.add(rejectAction);
        actions.add(applyAction);
        diffReq.putUserData(DiffUserDataKeysEx.CONTEXT_ACTIONS, actions);

        // Bottom panel
        JButton rejectBtn = new JButton("Reject", AllIcons.Actions.Cancel);
        rejectBtn.addActionListener(e -> {
            if (acted.compareAndSet(false, true)) { conn.disconnect(); future.complete(DiffResult.reject()); }
        });
        JButton applyBtn = new JButton("Apply", AllIcons.Actions.Checked);
        applyBtn.addActionListener(e -> {
            if (acted.compareAndSet(false, true)) {
                conn.disconnect(); cancelDismiss(dismissFuture);
                future.complete(DiffResult.apply(getContent(right, proposedFile)));
                closeView(project, chain);
            }
        });
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        panel.add(rejectBtn); panel.add(applyBtn);
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        bottom.add(panel);
        diffReq.putUserData(DiffUserDataKeysEx.BOTTOM_PANEL, bottom);

        conn.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override public void fileOpened(@NotNull FileEditorManager src, @NotNull VirtualFile f) {
                if (f instanceof ChainDiffVirtualFile dvf && dvf.getChain() == chain) {
                    cancelDismiss(dismissFuture);
                }
            }
            @Override public void fileClosed(@NotNull FileEditorManager src, @NotNull VirtualFile f) {
                if (f instanceof ChainDiffVirtualFile dvf && dvf.getChain() == chain) {
                    ScheduledFuture<?> sf = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                        .schedule(() -> ApplicationManager.getApplication().invokeLater(() -> {
                            if (acted.compareAndSet(false, true)) { conn.disconnect(); future.complete(DiffResult.dismiss()); }
                        }), DISMISS_DELAY_MS, TimeUnit.MILLISECONDS);
                    dismissFuture.set(sf);
                }
            }
        });

        DiffManagerEx.getInstance().showDiffBuiltin(project, chain, DiffDialogHints.DEFAULT);
        LOG.info("Interactive diff opened: " + req.getFilePath());
    }

    private static String getContent(DocumentContent dc, LightVirtualFile f) {
        return dc != null && dc.getDocument() != null ? dc.getDocument().getText() : f.getContent().toString();
    }

    private static void cancelDismiss(AtomicReference<ScheduledFuture<?>> ref) {
        ScheduledFuture<?> f = ref.getAndSet(null);
        if (f != null && !f.isDone()) f.cancel(false);
    }

    private static void closeView(Project project, SimpleDiffRequestChain chain) {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (VirtualFile f : FileEditorManager.getInstance(project).getOpenFiles()) {
                if (f instanceof ChainDiffVirtualFile dvf && dvf.getChain() == chain) {
                    FileEditorManager.getInstance(project).closeFile(f);
                    break;
                }
            }
        });
    }
}
