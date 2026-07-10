package com.protean.copilot.bridge;

import com.intellij.openapi.diagnostic.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight process registry for bridge-related child processes.
 */
public final class ProcessManager {

    private static final Logger LOG = Logger.getInstance(ProcessManager.class);

    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    public void register(String key, Process process) {
        if (key != null && process != null) {
            processes.put(key, process);
        }
    }

    public void unregister(String key, Process process) {
        if (key != null && process != null) {
            processes.remove(key, process);
        }
    }

    public Process get(String key) {
        return key == null ? null : processes.get(key);
    }

    public void terminate(Process process, String description) {
        if (process == null) {
            return;
        }
        if (process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    LOG.warn("Force killing process: " + description);
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    public void cleanup() {
        for (Map.Entry<String, Process> entry : processes.entrySet()) {
            terminate(entry.getValue(), entry.getKey());
        }
        processes.clear();
    }
}
