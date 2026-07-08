package com.protean.copilot.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DropTarget;

/**
 * 创建并配置渲染聊天界面的 JCEF 浏览器。
 * 处理浏览器生命周期、JS 桥接设置、拖放和错误恢复。
 *
 * 移植自 Claude Code GUI 参考实现。
 */
public class WebviewInitializer {

    private static final Logger LOG = Logger.getInstance(WebviewInitializer.class);

    /**
     * 宿主接口，提供对窗口级依赖项的访问。
     */
    public interface WebviewHost {
        Project getProject();
        JPanel getMainPanel();
        com.protean.copilot.util.HtmlLoader getHtmlLoader();
        JBCefBrowser getBrowser();
        void setBrowser(JBCefBrowser browser);
        boolean isDisposed();
        WebviewWatchdog getWebviewWatchdog();
        void setFrontendReady(boolean ready);
        void handleJavaScriptMessage(String message);
    }

    private final WebviewHost host;
    private JBCefJSQuery jsQuery = null;

    public WebviewInitializer(WebviewHost host) {
        this.host = host;
    }

    /**
     * 创建 JCEF 浏览器并设置聊天界面。
     * 在构造函数之后通过 ToolWindowManager.invokeLater() 延迟调用，
     * 以避免 JCEF 初始化冲突。
     */
    public void createUIComponents() {
        if (host.isDisposed()) return;

        try {
            LOG.info("Creating JCEF browser for project: " + host.getProject().getName());

            // 创建浏览器
            JBCefBrowser browser = new JBCefBrowser();
            host.setBrowser(browser);

            // 设置 JS 查询桥接（JS -> Java 通信）
            jsQuery = JBCefJSQuery.create(browser);
            if (jsQuery != null) {
                jsQuery.addHandler(message -> {
                    try {
                        host.handleJavaScriptMessage(message);
                    } catch (Exception e) {
                        LOG.warn("Error handling JS message: " + e.getMessage());
                    }
                    return null;
                });
            }

            // 注册加载处理器，在页面加载后注入 JS 桥接
            browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
                @Override
                public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                    if (frame.isMain()) {
                        injectJsBridge(browser, frame);
                        // 通知前端已就绪
                        host.setFrontendReady(true);
                    }
                }
            }, browser.getCefBrowser());

            // 设置拖放目标，用于拖放文件导入
            JPanel dropTargetPanel = new JPanel(new BorderLayout());
            dropTargetPanel.setDropTarget(new DropTarget());
            dropTargetPanel.add(browser.getComponent(), BorderLayout.CENTER);

            // 使用 JBCefBrowser 的便捷方法加载 HTML
            String html = host.getHtmlLoader().loadChatHtml();
            browser.loadHTML(html);

            // 添加到主面板
            replaceMainContent(dropTargetPanel);

            // 启动看门狗
            host.getWebviewWatchdog().start();

            LOG.info("JCEF browser created successfully for project: " + host.getProject().getName());

        } catch (Exception e) {
            LOG.error("Failed to create JCEF browser: " + e.getMessage(), e);
            showErrorPanel("Failed to initialize chat interface: " + e.getMessage());
        }
    }

    /**
     * 从头重新创建 webview（由看门狗在持续卡顿时使用）。
     */
    public void recreateWebview(String reason) {
        LOG.info("Recreating webview, reason: " + reason);
        host.getWebviewWatchdog().stop();

        try {
            JBCefBrowser oldBrowser = host.getBrowser();
            if (oldBrowser != null) {
                oldBrowser.dispose();
            }
        } catch (Exception e) {
            LOG.warn("Failed to dispose old browser during recreate: " + e.getMessage());
        }

        host.setFrontendReady(false);
        createUIComponents();
    }

    /**
     * 将 JS 桥接代码注入到已加载的页面中。
     * 使用 {@link JBCefJSQuery} 实现 JS→Java 通信，
     * 替代原有的 console.log 拦截方案，确保生产构建中桥接不被断开。
     */
    private void injectJsBridge(CefBrowser browser, CefFrame frame) {
        try {
            if (jsQuery == null) {
                LOG.warn("JBCefJSQuery not initialized, skipping bridge injection");
                return;
            }

            // 使用 JBCefJSQuery.inject() 生成原生 JCEF IPC 调用
            String injection = "window.sendToJava = function(msg) { "
                + jsQuery.inject("msg") + " };";
            frame.executeJavaScript(injection, frame.getURL(), 0);

            // 通知前端桥接已就绪
            frame.executeJavaScript(
                """
                (function() {
                    console.log('[Protean] JS bridge injected via JBCefJSQuery');
                })();
                """,
                frame.getURL(),
                0
            );

            LOG.info("JS bridge injected via JBCefJSQuery");
        } catch (Exception e) {
            LOG.warn("Failed to inject JS bridge: " + e.getMessage());
        }
    }

    /**
     * 在主内容区域显示错误面板。
     */
    public void showErrorPanel(String message) {
        LOG.warn("Showing error panel: " + message);
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        JLabel label = new JLabel("<html><body style='text-align:center;padding:40px;'>" +
                "<h2 style='color:#f85149;'>Chat Interface Error</h2>" +
                "<p>" + message + "</p></body></html>");
        panel.add(label);
        replaceMainContent(panel);
    }

    /**
     * 替换主面板的内容。
     */
    private void replaceMainContent(Component component) {
        JPanel panel = host.getMainPanel();
        panel.removeAll();
        panel.add(component, BorderLayout.CENTER);
        panel.revalidate();
        panel.repaint();
    }
}
