package com.protean.copilot.handler.diff;

public interface DiffActionHandler {

    String[] getSupportedTypes();

    default boolean supports(String type) {
        for (String supported : getSupportedTypes()) {
            if (supported.equals(type)) {
                return true;
            }
        }
        return false;
    }

    void handle(String type, String content);
}
