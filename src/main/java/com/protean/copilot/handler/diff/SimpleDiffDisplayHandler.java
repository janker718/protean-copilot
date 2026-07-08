package com.protean.copilot.handler.diff;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 处理非交互式差异显示请求。
 * 使用 IntelliJ 内置 DiffManager 打开只读差异查看器。
 */
public class SimpleDiffDisplayHandler {

    private static final Logger LOG = Logger.getInstance(SimpleDiffDisplayHandler.class);

    private final Project project;
    private final Gson gson;
    private final DiffFileOperations fileOps;

    public SimpleDiffDisplayHandler(Project project, Gson gson, DiffFileOperations fileOps) {
        this.project = project;
        this.gson = gson;
        this.fileOps = fileOps;
    }

    public String[] getSupportedTypes() {
        return new String[]{"show_diff", "show_multi_edit_diff", "show_edit_preview_diff", "show_edit_full_diff"};
    }

    public boolean handle(String type, String content) {
        return switch (type) {
            case "show_diff" -> { handleShowDiff(content); yield true; }
            case "show_multi_edit_diff" -> { handleShowMultiEditDiff(content); yield true; }
            case "show_edit_preview_diff" -> { handleShowEditPreviewDiff(content); yield true; }
            case "show_edit_full_diff" -> { handleShowEditFullDiff(content); yield true; }
            default -> false;
        };
    }

    private void handleShowDiff(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String filePath = getString(json, "filePath");
            String oldContent = getString(json, "oldContent");
            String newContent = getString(json, "newContent");
            String title = json.has("title") ? json.get("title").getAsString() : null;
            if (!fileOps.isPathWithinProject(filePath)) return;

            ApplicationManager.getApplication().invokeLater(() -> {
                String fileName = new File(filePath).getName();
                FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
                DiffContent left = DiffContentFactory.getInstance().create(project, oldContent, fileType);
                DiffContent right = DiffContentFactory.getInstance().create(project, newContent, fileType);
                DiffManager.getInstance().showDiff(project, new SimpleDiffRequest(
                    title != null ? title : "Diff: " + fileName, left, right,
                    fileName + " (Before)", fileName + " (After)"));
            });
        } catch (Exception e) { LOG.error("show_diff failed", e); }
    }

    private void handleShowMultiEditDiff(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String filePath = getString(json, "filePath");
            JsonArray edits = json.has("edits") ? json.getAsJsonArray("edits") : new JsonArray();
            String currentContent = json.has("currentContent") ? json.get("currentContent").getAsString() : null;
            if (!fileOps.isPathWithinProject(filePath)) return;

            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    String afterContent = currentContent;
                    if (afterContent == null) {
                        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
                        if (vf != null) {
                            vf.refresh(false, false);
                            afterContent = new String(vf.contentsToByteArray(), vf.getCharset());
                        }
                    }
                    if (afterContent == null) return;

                    // 反向应用编辑以得到原始内容
                    String beforeContent = afterContent;
                    for (int i = edits.size() - 1; i >= 0; i--) {
                        JsonObject edit = edits.get(i).getAsJsonObject();
                        String os = getString(edit, "oldString");
                        String ns = getString(edit, "newString");
                        boolean ra = edit.has("replaceAll") && edit.get("replaceAll").getAsBoolean();
                        beforeContent = reverseReplace(beforeContent, ns, os, ra);
                    }

                    showSimpleDiff(filePath, beforeContent, afterContent,
                        "Edit: " + new File(filePath).getName() + " (" + edits.size() + " edits)");
                } catch (Exception e) { LOG.error("show_multi_edit_diff failed", e); }
            });
        } catch (Exception e) { LOG.error("show_multi_edit_diff failed", e); }
    }

    private void handleShowEditPreviewDiff(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String filePath = getString(json, "filePath");
            JsonArray edits = json.has("edits") ? json.getAsJsonArray("edits") : new JsonArray();
            String title = json.has("title") ? json.get("title").getAsString() : null;
            if (!fileOps.isPathWithinProject(filePath)) return;

            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
                    String current = "";
                    if (vf != null && !vf.isDirectory()) {
                        vf.refresh(false, false);
                        current = new String(vf.contentsToByteArray(),
                            vf.getCharset() != null ? vf.getCharset() : StandardCharsets.UTF_8);
                    }
                    String after = applyEdits(current, edits);
                    String fileName = new File(filePath).getName();
                    showSimpleDiff(filePath, current, after,
                        title != null ? title : "Preview: " + fileName);
                } catch (Exception e) { LOG.error("show_edit_preview_diff failed", e); }
            });
        } catch (Exception e) { LOG.error("show_edit_preview_diff failed", e); }
    }

    private void handleShowEditFullDiff(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String filePath = getString(json, "filePath");
            String oldString = getString(json, "oldString");
            String newString = getString(json, "newString");
            String originalContent = json.has("originalContent") ? json.get("originalContent").getAsString() : null;
            boolean replaceAll = json.has("replaceAll") && json.get("replaceAll").getAsBoolean();
            String title = json.has("title") ? json.get("title").getAsString() : null;
            if (!fileOps.isPathWithinProject(filePath)) return;

            ApplicationManager.getApplication().invokeLater(() -> {
                String before, after;
                if (originalContent != null && !originalContent.isEmpty()) {
                    before = originalContent;
                    after = replaceContent(before, oldString, newString, replaceAll);
                } else {
                    before = oldString;
                    after = newString;
                }
                String fileName = new File(filePath).getName();
                showSimpleDiff(filePath, before, after,
                    title != null ? title : "Edit: " + fileName);
            });
        } catch (Exception e) { LOG.error("show_edit_full_diff failed", e); }
    }

    // -- helpers --

    private void showSimpleDiff(String filePath, String before, String after, String title) {
        String fileName = new File(filePath).getName();
        FileType ft = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
        DiffContent left = DiffContentFactory.getInstance().create(project, before, ft);
        DiffContent right = DiffContentFactory.getInstance().create(project, after, ft);
        DiffManager.getInstance().showDiff(project, new SimpleDiffRequest(
            title, left, right, fileName + " (Before)", fileName + " (After)"));
    }

    private String getString(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsString() : "";
    }

    private String applyEdits(String content, JsonArray edits) {
        String result = content;
        for (int i = 0; i < edits.size(); i++) {
            JsonObject edit = edits.get(i).getAsJsonObject();
            result = replaceContent(result, getString(edit, "oldString"),
                getString(edit, "newString"),
                edit.has("replaceAll") && edit.get("replaceAll").getAsBoolean());
        }
        return result;
    }

    private String replaceContent(String content, String oldStr, String newStr, boolean replaceAll) {
        if (replaceAll) return content.replace(oldStr, newStr);
        int idx = content.indexOf(oldStr);
        if (idx >= 0) return content.substring(0, idx) + newStr + content.substring(idx + oldStr.length());
        return content;
    }

    private String reverseReplace(String content, String oldStr, String newStr, boolean replaceAll) {
        if (replaceAll) return content.replace(newStr, oldStr);
        int idx = content.indexOf(newStr);
        if (idx >= 0) return content.substring(0, idx) + oldStr + content.substring(idx + newStr.length());
        return content;
    }
}
