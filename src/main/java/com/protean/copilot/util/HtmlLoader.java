package com.protean.copilot.util;

import com.intellij.openapi.diagnostic.Logger;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 从资源中加载聊天界面 HTML 的 HTML 加载器。
 * 从 Claude Code GUI 参考实现移植。
 */
public class HtmlLoader {

    private final Logger log = Logger.getInstance(HtmlLoader.class);
    private final Class<?> resourceClass;

    public HtmlLoader(Class<?> resourceClass) {
        this.resourceClass = resourceClass;
    }

    /**
     * 加载聊天界面 HTML。
     * @return HTML 内容，如果加载失败则返回备用 HTML
     */
    public String loadChatHtml() {
        try {
            InputStream inputStream = resourceClass.getResourceAsStream("/html/protean-chat.html");
            if (inputStream != null) {
                String html = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                inputStream.close();
                return injectIdeTheme(html);
            }
        } catch (Exception e) {
            log.error("Failed to load protean-chat.html: " + e.getMessage());
        }
        return generateFallbackHtml();
    }

    /**
     * 将 IDE 主题注入 HTML，以防止未样式化内容的闪烁。
     */
    private String injectIdeTheme(String html) {
        try {
            boolean isDark = ThemeConfigService.isDarkTheme();
            String theme = isDark ? "dark" : "light";
            String bgColor = ThemeConfigService.getBackgroundColorHex();

            String result = html;
            // 1. 修改 <html> 标签
            result = result.replaceFirst(
                "<html([^>]*)>",
                "<html$1 style=\"background-color:" + bgColor + ";\">"
            );
            // 2. 修改 <body> 标签
            result = result.replaceFirst(
                "<body([^>]*)>",
                "<body$1 style=\"background-color:" + bgColor + ";\">"
            );
            // 3. 在 <head> 之后注入主题变量脚本
            String scriptInjection = "\n    <script>window.__INITIAL_IDE_THEME__ = '" + theme + "';</script>";
            int headIndex = result.indexOf("<head>");
            if (headIndex != -1) {
                int insertPos = headIndex + "<head>".length();
                result = result.substring(0, insertPos) + scriptInjection + result.substring(insertPos);
            }

            log.info("Successfully injected IDE theme: " + theme + ", background: " + bgColor);
            return result;
        } catch (Exception e) {
            log.error("Failed to inject IDE theme: " + e.getMessage(), e);
        }
        return html;
    }

    /**
     * 当主 HTML 文件无法加载时生成备用 HTML。
     */
    public String generateFallbackHtml() {
        boolean isDark;
        try {
            isDark = ThemeConfigService.isDarkTheme();
        } catch (Exception e) {
            isDark = true;
        }
        String bgColor = isDark ? "#1e1e1e" : "#ffffff";
        String textColor = isDark ? "#cccccc" : "#333333";
        String headingColor = isDark ? "#f85149" : "#d32f2f";

        return """
            <!DOCTYPE html>
            <html style="background-color:%s;">
            <head>
            <meta charset="UTF-8">
            <title>Protean Copilot</title>
            <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            background: %s; color: %s; display: flex; align-items: center;
            justify-content: center; height: 100vh; margin: 0; }
            .error { text-align: center; padding: 40px; }
            h1 { color: %s; }
            </style>
            </head>
            <body>
            <div class="error">
            <h1>Failed to load chat interface</h1>
            <p>Please verify that the HTML resource file exists</p>
            </div>
            </body>
            </html>
            """.formatted(bgColor, bgColor, textColor, headingColor);
    }
}
