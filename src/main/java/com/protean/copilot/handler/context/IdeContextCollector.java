package com.protean.copilot.handler.context;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class IdeContextCollector {

    private IdeContextCollector() {
        // private constructor for utility class
    }

    public static IdeContext collect(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            throw new RuntimeException("Project is required to collect IDE context");
        }
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        }
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) {
            VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
            file = (selectedFiles.length > 0) ? selectedFiles[0] : null;
        }

        return collect(project, editor, file);
    }

    public static IdeContext collect(Project project) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
        VirtualFile file = (selectedFiles.length > 0) ? selectedFiles[0] : null;
        return collect(project, editor, file);
    }

    public static IdeContext collect(Project project, Editor editor, VirtualFile file) {
        return ReadAction.compute(() -> {
            Editor effectiveEditor = (editor != null) ? editor : selectedTextEditor(project);
            VirtualFile effectiveFile = (file != null) ? file : selectedFile(project);
            CurrentFile currentFile = (effectiveFile != null) ? toCurrentFile(effectiveFile) : null;
            Selection selection = (effectiveEditor != null) ? toSelection(effectiveEditor) : null;
            List<String> openFiles = Arrays.stream(
                    FileEditorManager.getInstance(project).getOpenFiles()
                )
                .map(VirtualFile::getPath)
                .distinct()
                .collect(Collectors.toList());

            return new IdeContext(
                project.getName(),
                project.getBasePath(),
                currentFile,
                selection,
                openFiles
            );
        });
    }

    private static Editor selectedTextEditor(Project project) {
        var selectedEditor = FileEditorManager.getInstance(project).getSelectedEditor();
        if (selectedEditor instanceof TextEditor textEditor) {
            return textEditor.getEditor();
        }
        return null;
    }

    private static VirtualFile selectedFile(Project project) {
        VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
        return (selectedFiles.length > 0) ? selectedFiles[0] : null;
    }

    private static CurrentFile toCurrentFile(VirtualFile vf) {
        var document = FileDocumentManager.getInstance().getDocument(vf);
        return new CurrentFile(
            vf.getPath(),
            vf.getName(),
            vf.getFileType().getName(),
            (document != null) ? document.getText() : null
        );
    }

    private static Selection toSelection(Editor editor) {
        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isBlank()) {
            return null;
        }
        int startOffset = editor.getSelectionModel().getSelectionStart();
        int endOffset = Math.max(editor.getSelectionModel().getSelectionEnd(), startOffset);
        int startLine = editor.getDocument().getLineNumber(startOffset) + 1;
        int endLine = editor.getDocument().getLineNumber(endOffset) + 1;

        return new Selection(
            startLine,
            endLine,
            selectedText
        );
    }
}
