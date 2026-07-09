package com.protean.copilot.util;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefBrowserBuilder;

/**
 * Centralized JCEF browser creation.
 */
public final class JBCefBrowserFactory {

    private static final Logger LOG = Logger.getInstance(JBCefBrowserFactory.class);

    private JBCefBrowserFactory() {
    }

    public static JBCefBrowser create() {
        boolean offScreenRendering = determineOsrMode();
        try {
            JBCefBrowser browser = JBCefBrowser.createBuilder()
                .setOffScreenRendering(offScreenRendering)
                .build();
            browser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true);
            return browser;
        } catch (Exception e) {
            LOG.warn("JBCefBrowser builder failed, falling back to default constructor", e);
            JBCefBrowser browser = new JBCefBrowser();
            browser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true);
            return browser;
        }
    }

    public static boolean isJcefSupported() {
        try {
            return JBCefApp.isSupported();
        } catch (Exception e) {
            LOG.warn("Failed to check JCEF support", e);
            return false;
        }
    }

    private static boolean determineOsrMode() {
        if (SystemInfo.isMac || SystemInfo.isWindows) {
            return false;
        }
        if (SystemInfo.isLinux || SystemInfo.isUnix) {
            return getIdeaMajorVersion() >= 2023;
        }
        return false;
    }

    private static int getIdeaMajorVersion() {
        try {
            return Integer.parseInt(ApplicationInfo.getInstance().getMajorVersion());
        } catch (Exception e) {
            LOG.warn("Failed to resolve IDEA major version", e);
            return 0;
        }
    }
}
