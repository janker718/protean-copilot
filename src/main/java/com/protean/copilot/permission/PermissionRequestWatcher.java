package com.protean.copilot.permission;

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.function.BiConsumer;

/**
 * Polls the permission directory and dispatches session-scoped request files.
 */
class PermissionRequestWatcher {

    interface RequestHandler {
        void handlePermissionRequest(Path requestFile);
        void handleAskUserQuestionRequest(Path requestFile);
        void handlePlanApprovalRequest(Path requestFile);
    }

    private static final Logger LOG = Logger.getInstance(PermissionRequestWatcher.class);
    private static final int POLL_INTERVAL_MS = 500;
    private static final int ERROR_RETRY_DELAY_MS = 1000;

    private final Path permissionDir;
    private final String sessionId;
    private final PermissionFileProtocol fileProtocol;
    private final BiConsumer<String, String> debugLog;

    private volatile boolean running;
    private Thread watchThread;

    PermissionRequestWatcher(
        Path permissionDir,
        String sessionId,
        PermissionFileProtocol fileProtocol,
        BiConsumer<String, String> debugLog
    ) {
        this.permissionDir = permissionDir;
        this.sessionId = sessionId;
        this.fileProtocol = fileProtocol;
        this.debugLog = debugLog;
    }

    void start(RequestHandler handler) {
        if (running) {
            return;
        }
        fileProtocol.cleanupSessionFiles();
        running = true;
        watchThread = new Thread(() -> watchLoop(handler), "PermissionWatcher-" + sessionId);
        watchThread.setDaemon(true);
        watchThread.start();
    }

    void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
            try {
                watchThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Error occurred", e);
            }
        }
    }

    private void watchLoop(RequestHandler handler) {
        while (running) {
            try {
                File dir = permissionDir.toFile();
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                dispatchFiles(fileProtocol.listPermissionRequestFiles(), handler::handlePermissionRequest);
                dispatchFiles(fileProtocol.listAskUserQuestionRequestFiles(), handler::handleAskUserQuestionRequest);
                dispatchFiles(fileProtocol.listPlanApprovalRequestFiles(), handler::handlePlanApprovalRequest);

                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                debugLog.accept("POLL_ERROR", "Error in poll loop: " + e.getMessage());
                LOG.error("Error occurred", e);
                try {
                    Thread.sleep(ERROR_RETRY_DELAY_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void dispatchFiles(File[] files, java.util.function.Consumer<Path> consumer) {
        for (File file : files) {
            if (file.exists()) {
                consumer.accept(file.toPath());
            }
        }
    }
}
