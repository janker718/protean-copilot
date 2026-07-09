package com.protean.copilot.util;

import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for sanitizing and truncating text safely for persistence.
 */
public final class TextSanitizer {

    private TextSanitizer() {
    }

    public static @Nullable String sanitizeInvalidSurrogates(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    builder.append(current).append(text.charAt(i + 1));
                    i++;
                } else {
                    builder.append('\uFFFD');
                }
            } else if (Character.isLowSurrogate(current)) {
                builder.append('\uFFFD');
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    public static @Nullable String sanitizeAndTruncateSingleLine(@Nullable String text, int maxCodePoints) {
        String sanitized = sanitizeInvalidSurrogates(text);
        if (sanitized == null) {
            return null;
        }

        sanitized = sanitized
            .replace("\r\n", " ")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .trim();
        if (sanitized.isEmpty()) {
            return null;
        }

        int codePointCount = sanitized.codePointCount(0, sanitized.length());
        if (codePointCount > maxCodePoints) {
            int endIndex = sanitized.offsetByCodePoints(0, maxCodePoints);
            return sanitized.substring(0, endIndex) + "...";
        }
        return sanitized;
    }
}
