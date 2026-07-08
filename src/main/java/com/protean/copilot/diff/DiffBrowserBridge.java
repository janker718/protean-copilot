package com.protean.copilot.diff;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.jcef.JBCefBrowser;
import com.protean.copilot.util.JsUtils;

/**
 * Diff 操作的浏览器通信桥接 —— Java → WebView JavaScript 调用。
 */
public class DiffBrowserBridge {

    private static final Logger LOG = Logger.getInstance(DiffBrowserBridge.class);
    private final JBCefBrowser browser;
    private final Gson gson;

    public DiffBrowserBridge(JBCefBrowser browser, Gson gson) {
        this.browser = browser;
        this.gson = gson;
    }

    /** 在 WebView 中显示错误 Toast。 */
    public void showErrorToast(String message) {
        if (browser == null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                String escaped = JsUtils.escapeJs(message);
                browser.getCefBrowser().executeJavaScript(
                    "if(window.addToast){window.addToast('" + escaped + "','error');}",
                    browser.getCefBrowser().getURL(), 0);
            } catch (Exception e) {
                LOG.error("Failed to show error toast: " + e.getMessage());
            }
        });
    }

    /** 通知前端从编辑列表中移除文件。 */
    public void sendRemoveFileFromEdits(String filePath) {
        if (browser == null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("filePath", filePath);
                String json = gson.toJson(payload);
                browser.getCefBrowser().executeJavaScript(
                    "(function(){if(typeof window.handleRemoveFileFromEdits==='function')"
                    + "{window.handleRemoveFileFromEdits('" + JsUtils.escapeJs(json) + "');}})();",
                    browser.getCefBrowser().getURL(), 0);
            } catch (Exception e) {
                LOG.error("Failed to send remove_file_from_edits: " + e.getMessage());
            }
        });
    }

    /** 将 diff 结果发送到前端。 */
    public void sendDiffResult(String filePath, String action, String content, String error) {
        if (browser == null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("filePath", filePath);
                payload.addProperty("action", action);
                if (content != null) payload.addProperty("content", content);
                if (error != null) payload.addProperty("error", error);
                String json = gson.toJson(payload);
                browser.getCefBrowser().executeJavaScript(
                    "(function(){if(typeof window.handleDiffResult==='function')"
                    + "{window.handleDiffResult('" + JsUtils.escapeJs(json) + "');}})();",
                    browser.getCefBrowser().getURL(), 0);
                LOG.info("Diff result sent: " + action + " for " + filePath);
            } catch (Exception e) {
                LOG.error("Failed to send diff_result: " + e.getMessage());
            }
        });
    }
}
