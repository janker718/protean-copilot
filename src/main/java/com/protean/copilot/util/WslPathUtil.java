package com.protean.copilot.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Minimal path-boundary helper aligned with the reference permission manager.
 */
public final class WslPathUtil {

    private WslPathUtil() {
    }

    public static boolean isWslPath(String path) {
        return path != null && !path.isEmpty() && path.charAt(0) == '/';
    }

    public static boolean isPathWithinDirectory(String filePath, String basePath) {
        if (filePath == null || filePath.isEmpty() || basePath == null || basePath.isEmpty()) {
            return false;
        }
        if (isWslPath(filePath) || isWslPath(basePath)) {
            String normalizedFile = foldPosix(filePath);
            String normalizedBase = foldPosix(basePath);
            if (normalizedFile == null || normalizedBase == null) {
                return false;
            }
            String basePrefix = normalizedBase.endsWith("/") ? normalizedBase : normalizedBase + "/";
            return normalizedFile.equals(normalizedBase) || normalizedFile.startsWith(basePrefix);
        }
        try {
            String canonicalFile = new File(filePath).getCanonicalPath();
            String canonicalBase = new File(basePath).getCanonicalPath();
            return canonicalFile.startsWith(canonicalBase + File.separator) || canonicalFile.equals(canonicalBase);
        } catch (IOException e) {
            return false;
        }
    }

    public static String foldPosix(String path) {
        if (path == null || path.isEmpty() || path.charAt(0) != '/') {
            return null;
        }
        Deque<String> stack = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (stack.isEmpty()) {
                    return null;
                }
                stack.removeLast();
            } else {
                stack.addLast(segment);
            }
        }
        if (stack.isEmpty()) {
            return "/";
        }
        StringBuilder sb = new StringBuilder();
        for (String segment : stack) {
            sb.append('/').append(segment);
        }
        return sb.toString();
    }

    public static String toVfsPath(String path) {
        return path == null ? null : path.replace('\\', '/');
    }
}
