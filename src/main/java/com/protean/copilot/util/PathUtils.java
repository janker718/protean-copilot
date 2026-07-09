package com.protean.copilot.util;

/**
 * Path utility helpers shared by history readers.
 */
public final class PathUtils {

    private PathUtils() {
    }

    /**
     * Consistent with Claude Code's sanitizedPath logic.
     */
    public static String sanitizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.replaceAll("[^a-zA-Z0-9]", "-");
    }
}
