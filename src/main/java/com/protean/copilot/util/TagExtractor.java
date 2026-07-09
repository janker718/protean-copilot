package com.protean.copilot.util;

/**
 * Utility class for extracting content from XML-like tags.
 */
public final class TagExtractor {

    private TagExtractor() {
    }

    public static String extractCommandMessageContent(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String message = extractTagContent(text, "command-message");
        String args = extractTagContent(text, "command-args");

        if ((message == null || message.isEmpty()) && (args == null || args.isEmpty())) {
            return text;
        }

        StringBuilder builder = new StringBuilder();
        if (message != null && !message.isEmpty()) {
            builder.append(message);
        }
        if (args != null && !args.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(args);
        }
        return builder.length() > 0 ? builder.toString() : text;
    }

    public static String extractTagContent(String text, String tagName) {
        if (text == null || text.isEmpty() || tagName == null || tagName.isEmpty()) {
            return null;
        }

        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";

        int start = text.indexOf(openTag);
        if (start < 0) {
            return null;
        }
        int end = text.indexOf(closeTag, start + openTag.length());
        if (end < 0) {
            return null;
        }

        String content = text.substring(start + openTag.length(), end).trim();
        return content.isEmpty() ? null : content;
    }
}
