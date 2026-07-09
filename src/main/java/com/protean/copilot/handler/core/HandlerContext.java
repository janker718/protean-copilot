package com.protean.copilot.handler.core;

import com.protean.copilot.bridge.SdkBridge;
import com.protean.copilot.permission.PermissionService;
import com.protean.copilot.session.ChatSession;
import com.protean.copilot.settings.SettingsService;
import com.protean.copilot.util.JsUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;

/**
 * 所有消息处理器的共享上下文。
 * 提供对项目、桥接、会话、浏览器和 JavaScript 调用能力的访问。
 *
 * 从参考实现的 HandlerContext 移植而来。
 */
public class HandlerContext {

    private static final Logger LOG = Logger.getInstance(HandlerContext.class);

    /**
     * 用于在 webview 中调用 JavaScript 的回调接口。
     */
    public interface JsCallback {
        void callJavaScript(String functionName, String... args);
        String escapeJs(String str);
    }

    public final Project project;
    public final SdkBridge sdkBridge;
    public final SettingsService settingsService;
    public final JsCallback jsCallback;

    public volatile ChatSession session = null;
    public volatile JBCefBrowser browser = null;
    public volatile PermissionService permissionService = null;
    private volatile String currentModel = "default";
    private volatile String currentProvider = "protean";
    public volatile boolean isDisposed = false;

    public HandlerContext(Project project, SdkBridge sdkBridge, SettingsService settingsService, JsCallback jsCallback) {
        this.project = project;
        this.sdkBridge = sdkBridge;
        this.settingsService = settingsService;
        this.jsCallback = jsCallback;
    }

    public ChatSession getSession() { return session; }
    public void setSession(ChatSession session) { this.session = session; }

    public Project getProject() { return project; }

    public SettingsService getSettingsService() { return settingsService; }

    public JBCefBrowser getBrowser() { return browser; }
    public void setBrowser(JBCefBrowser browser) { this.browser = browser; }

    public PermissionService getPermissionService() { return permissionService; }
    public void setPermissionService(PermissionService permissionService) { this.permissionService = permissionService; }

    public String getCurrentModel() { return currentModel; }
    public void setCurrentModel(String currentModel) { this.currentModel = currentModel; }

    public String getCurrentProvider() { return currentProvider; }
    public void setCurrentProvider(String currentProvider) { this.currentProvider = currentProvider; }

    public boolean isDisposed() { return isDisposed; }
    public void setDisposed(boolean disposed) { isDisposed = disposed; }

    /**
     * 在浏览器中执行原始 JavaScript 代码，始终在 EDT 上运行。
     */
    public void executeJavaScriptOnEDT(String jsCode) {
        if (isDisposed) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (isDisposed) return;
            JBCefBrowser b = browser;
            if (b != null) {
                try {
                    b.getCefBrowser().executeJavaScript(jsCode, b.getCefBrowser().getURL(), 0);
                } catch (Exception e) {
                    LOG.warn("Failed to execute JavaScript: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 按名称调用 JavaScript 函数，安全地转义参数。
     * 始终在 EDT 上运行。
     */
    public void callJavaScript(String functionName, String... args) {
        jsCallback.callJavaScript(functionName, args);
    }

    /**
     * 转义字符串以安全地嵌入 JavaScript 中。
     */
    public String escapeJs(String str) {
        return JsUtils.escapeJs(str);
    }
}
