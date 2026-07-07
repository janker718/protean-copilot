package com.protean.copilot.notifications;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

public final class ProteanNotifier {

    private ProteanNotifier() {
        // 工具类，禁止实例化
    }

    private static final String GROUP_ID = "ProteanCopilot Notifications";
    private static final String TITLE = "Protean Copilot";

    public static void info(Project project, String message) {
        notify(project, message, NotificationType.INFORMATION);
    }

    public static void warning(Project project, String message) {
        notify(project, message, NotificationType.WARNING);
    }

    public static void error(Project project, String message) {
        notify(project, message, NotificationType.ERROR);
    }

    private static void notify(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification(TITLE, message, type)
                .notify(project);
    }
}
